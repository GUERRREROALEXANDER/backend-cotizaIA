package com.cotizaia.ingest.gmail;

import static com.cotizaia.ingest.gmail.GmailFixtures.LABEL;
import static com.cotizaia.ingest.gmail.GmailFixtures.plainMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotizaia.crypto.BlindIndex;
import com.cotizaia.domain.Agency;
import com.cotizaia.domain.Brief;
import com.cotizaia.domain.BriefChannel;
import com.cotizaia.domain.Client;
import com.cotizaia.facade.QuotationFacade;
import com.cotizaia.ingest.gmail.GmailClient.HistoryExpiredException;
import com.cotizaia.ingest.gmail.GmailClient.HistoryPage;
import com.cotizaia.ingest.gmail.GmailIngestedMessage.Status;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.BriefRepository;
import com.cotizaia.repository.ClientRepository;
import com.cotizaia.service.BriefService;
import com.cotizaia.service.ClientService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spec 11 acceptance: a labelled email becomes exactly one EMAIL brief through
 * the BriefService/registry path, then goes to the QuotationFacade.
 */
@SpringBootTest
@ActiveProfiles("test")
class GmailIngestServiceTests {

    private final GmailClient gmail = mock(GmailClient.class);

    private final QuotationFacade facade = mock(QuotationFacade.class);

    private GmailIngestService ingest;

    private Long agencyId;

    @Autowired
    private GmailIngestedMessageRepository ledger;

    @Autowired
    private GmailSyncStateRepository cursors;

    @Autowired
    private AgencyRepository agencies;

    @Autowired
    private ClientRepository clients;

    @Autowired
    private ClientService clientService;

    @Autowired
    private BriefService briefService;

    @Autowired
    private BriefRepository briefs;

    @Autowired
    private BlindIndex blindIndex;

    /**
     * Built by hand around the real repositories, BriefService (and so the real
     * BriefSourceRegistry and EmailBriefAdapter): only Gmail and the facade are
     * doubles, and the shared test context is reused.
     */
    @BeforeEach
    void setUp() {
        ledger.deleteAll();
        cursors.deleteAll();
        agencyId = agencies.saveAndFlush(new Agency("Agencia Gmail")).getId();
        GmailProperties properties = new GmailProperties(true, "cotiza@test.co", "cotizar", agencyId, null, false,
                true, true, 20, null, null);
        ingest = new GmailIngestService(gmail, properties, ledger, cursors, clients, clientService, blindIndex,
                briefService, facade, Clock.systemUTC());
        when(gmail.labelId("cotizar")).thenReturn(LABEL);
        when(gmail.currentHistoryId()).thenReturn(100L);
    }

    @Test
    void firstSyncOnlyStoresTheCursorAndBackfillsNothing() {
        assertThat(ingest.sync()).isZero();

        assertThat(cursors.findById("cotiza@test.co").orElseThrow().getHistoryId()).isEqualTo(100L);
        verify(gmail, never()).history(anyLong(), anyString());
    }

    @Test
    void labelledMailBecomesOneEmailBriefForTheKnownClientAndIsProcessed() {
        Client known = knownClient("laura@sabor.co");
        startCursorAt(100L);
        when(gmail.history(100L, LABEL)).thenReturn(new HistoryPage(List.of("g1"), 120L));
        when(gmail.message("g1")).thenReturn(plainMessage("g1", "Laura <Laura@Sabor.co>", "App de pedidos",
                "Necesitamos   una app de pedidos con pagos.", List.of("INBOX", LABEL)));

        assertThat(ingest.sync()).isEqualTo(1);

        GmailIngestedMessage row = ledger.findById("g1").orElseThrow();
        assertThat(row.getStatus()).isEqualTo(Status.PROCESSED);
        Brief brief = briefs.findWithClientById(row.getBriefId()).orElseThrow();
        assertThat(brief.getSource()).isEqualTo(BriefChannel.EMAIL);
        assertThat(brief.getClient().getId()).isEqualTo(known.getId());
        // Whitespace normalized by the EmailBriefAdapter, not by the Gmail code.
        assertThat(brief.getRawText()).isEqualTo("Necesitamos una app de pedidos con pagos.");
        assertThat(brief.getRawPayload()).contains("\"subject\":\"App de pedidos\"").contains("\"gmailMessageId\":\"g1\"");
        assertThat(brief.getReceivedAt()).isEqualTo(Instant.ofEpochMilli(1762560000000L));
        verify(facade).processBrief(agencyId, brief.getId());
        assertThat(cursors.findById("cotiza@test.co").orElseThrow().getHistoryId()).isEqualTo(120L);
    }

    @Test
    void theSameMessageSeenTwiceCreatesOneBrief() {
        knownClient("dup@acme.co");
        startCursorAt(100L);
        when(gmail.history(anyLong(), eq(LABEL))).thenReturn(new HistoryPage(List.of("g2", "g2"), 130L));
        when(gmail.message("g2")).thenReturn(plainMessage("g2", "dup@acme.co", "", "Una landing.", List.of(LABEL)));
        long before = briefs.count();

        ingest.sync();
        ingest.sync();

        assertThat(briefs.count()).isEqualTo(before + 1);
        verify(gmail, times(1)).message("g2");
    }

    @Test
    void mailWithoutTheLabelIsIgnored() {
        startCursorAt(100L);
        when(gmail.history(100L, LABEL)).thenReturn(new HistoryPage(List.of("g3"), 140L));
        when(gmail.message("g3")).thenReturn(plainMessage("g3", "x@y.co", "", "Spam", List.of("INBOX")));
        long before = briefs.count();

        assertThat(ingest.sync()).isZero();

        assertThat(briefs.count()).isEqualTo(before);
        assertThat(ledger.existsById("g3")).isFalse();
    }

    @Test
    void anUnknownSenderBecomesANewClientOfTheMailboxAgency() {
        startCursorAt(100L);
        when(gmail.history(100L, LABEL)).thenReturn(new HistoryPage(List.of("g4"), 150L));
        when(gmail.message("g4")).thenReturn(plainMessage("g4", "\"Pedro Ruiz\" <pedro@nuevo.co>", "",
                "Tienda online.", List.of(LABEL)));

        ingest.sync();

        Client created = clients.findByAgencyIdAndEmailHash(agencyId, blindIndex.hash("pedro@nuevo.co")).orElseThrow();
        assertThat(created.getName()).isEqualTo("Pedro Ruiz");
    }

    @Test
    void aProcessingFailureKeepsTheBrief() {
        knownClient("fail@acme.co");
        startCursorAt(100L);
        when(gmail.history(100L, LABEL)).thenReturn(new HistoryPage(List.of("g5"), 160L));
        when(gmail.message("g5")).thenReturn(plainMessage("g5", "fail@acme.co", "", "Un CRM.", List.of(LABEL)));
        when(facade.processBrief(anyLong(), anyLong())).thenThrow(new IllegalStateException("llm down"));

        assertThat(ingest.sync()).isEqualTo(1);

        GmailIngestedMessage row = ledger.findById("g5").orElseThrow();
        assertThat(row.getStatus()).isEqualTo(Status.PROCESS_FAILED);
        assertThat(row.getBriefId()).isNotNull();
        assertThat(row.getError()).contains("llm down");
    }

    @Test
    void anExpiredCursorFallsBackToALabelSearchAndResets() {
        knownClient("late@acme.co");
        startCursorAt(5L);
        when(gmail.history(5L, LABEL)).thenThrow(new HistoryExpiredException("gone"));
        when(gmail.currentHistoryId()).thenReturn(900L);
        when(gmail.search(eq("label:cotizar newer_than:7d"), anyInt())).thenReturn(List.of("g6"));
        when(gmail.message("g6")).thenReturn(plainMessage("g6", "late@acme.co", "", "Una app.", List.of(LABEL)));

        assertThat(ingest.sync()).isEqualTo(1);

        assertThat(cursors.findById("cotiza@test.co").orElseThrow().getHistoryId()).isEqualTo(900L);
    }

    @Test
    void anEmptyBodyIsRecordedAndSkipped() {
        startCursorAt(100L);
        when(gmail.history(100L, LABEL)).thenReturn(new HistoryPage(List.of("g7"), 170L));
        when(gmail.message("g7")).thenReturn(plainMessage("g7", "a@b.co", "solo asunto", "   ", List.of(LABEL)));

        assertThat(ingest.sync()).isZero();

        assertThat(ledger.findById("g7").orElseThrow().getStatus()).isEqualTo(Status.SKIPPED_EMPTY);
        verify(facade, never()).processBrief(any(), any());
    }

    private Client knownClient(String email) {
        Agency agency = agencies.findById(agencyId).orElseThrow();
        Client client = new Client(agency, "Cliente " + email, email);
        client.setEmailHash(blindIndex.hash(email));
        return clients.saveAndFlush(client);
    }

    private void startCursorAt(long historyId) {
        GmailSyncState state = new GmailSyncState("cotiza@test.co", Instant.now());
        state.setHistoryId(historyId, Instant.now());
        cursors.save(state);
    }
}
