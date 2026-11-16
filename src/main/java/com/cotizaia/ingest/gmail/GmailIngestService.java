package com.cotizaia.ingest.gmail;

import com.cotizaia.crypto.BlindIndex;
import com.cotizaia.domain.Brief;
import com.cotizaia.domain.BriefChannel;
import com.cotizaia.domain.Client;
import com.cotizaia.facade.QuotationFacade;
import com.cotizaia.ingest.gmail.GmailClient.HistoryExpiredException;
import com.cotizaia.ingest.gmail.GmailClient.HistoryPage;
import com.cotizaia.ingest.gmail.GmailIngestedMessage.Status;
import com.cotizaia.repository.ClientRepository;
import com.cotizaia.service.BriefService;
import com.cotizaia.service.ClientService;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns new mail with the "cotizar" label into briefs (spec 11).
 *
 * <p>Gmail history is the source of truth for "new": poll and push both call
 * {@link #sync()}, which reads {@code users.history.list} from the stored
 * cursor. A push notification is only a doorbell; its historyId is not
 * trusted, so a forged or replayed push can at most trigger an extra read.
 *
 * <p>The brief is created through {@link BriefService}, which resolves the
 * {@code EmailBriefAdapter} from the {@code BriefSourceRegistry}: this class
 * never builds a Brief itself. Processing then goes through the
 * {@link QuotationFacade}, like a brief created from the UI.
 */
public class GmailIngestService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GmailIngestService.class);

    private final GmailClient gmail;

    private final GmailProperties properties;

    private final GmailIngestedMessageRepository ledger;

    private final GmailSyncStateRepository cursors;

    private final ClientRepository clients;

    private final ClientService clientService;

    private final BlindIndex blindIndex;

    private final BriefService briefService;

    private final QuotationFacade facade;

    private final Clock clock;

    /** One worker: syncs never overlap, so the cursor cannot move backwards. */
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "gmail-ingest");
        thread.setDaemon(true);
        return thread;
    });

    private volatile String labelId;

    public GmailIngestService(GmailClient gmail, GmailProperties properties, GmailIngestedMessageRepository ledger,
            GmailSyncStateRepository cursors, ClientRepository clients, ClientService clientService,
            BlindIndex blindIndex, BriefService briefService, QuotationFacade facade, Clock clock) {
        if (properties.agencyId() == null || properties.mailbox() == null || properties.mailbox().isBlank()) {
            throw new IllegalStateException("gmail.enabled=true needs GMAIL_AGENCY_ID and GMAIL_MAILBOX");
        }
        this.gmail = gmail;
        this.properties = properties;
        this.ledger = ledger;
        this.cursors = cursors;
        this.clients = clients;
        this.clientService = clientService;
        this.blindIndex = blindIndex;
        this.briefService = briefService;
        this.facade = facade;
        this.clock = clock;
    }

    /** Queues a sync and returns at once (the push endpoint must answer fast). */
    public void requestSync() {
        worker.execute(this::syncQuietly);
    }

    void syncQuietly() {
        try {
            sync();
        } catch (RuntimeException e) {
            LOGGER.warn("Gmail sync failed: {}", e.toString());
        }
    }

    /**
     * Reads history since the cursor and ingests every new labelled message.
     * The first run only stores the current historyId: mail that arrived
     * before the integration was switched on is not backfilled.
     *
     * @return the number of briefs created
     */
    public synchronized int sync() {
        GmailSyncState state = cursors.findById(properties.mailbox())
                .orElseGet(() -> new GmailSyncState(properties.mailbox(), now()));
        if (state.getHistoryId() == null) {
            state.setHistoryId(gmail.currentHistoryId(), now());
            cursors.save(state);
            LOGGER.info("Gmail intake started for {}; cursor at historyId {}", properties.mailbox(),
                    state.getHistoryId());
            return 0;
        }
        List<String> ids;
        long nextCursor;
        try {
            HistoryPage page = gmail.history(state.getHistoryId(), labelId());
            ids = page.messageIds();
            nextCursor = page.historyId();
        } catch (HistoryExpiredException e) {
            // The cursor fell out of Gmail's history window (server down for days).
            // Recover with a bounded search; the ledger makes re-reading harmless.
            LOGGER.warn("Gmail history cursor expired, falling back to a label search: {}", e.getMessage());
            nextCursor = gmail.currentHistoryId();
            ids = gmail.search("label:" + properties.label() + " newer_than:7d", properties.maxMessagesPerRun());
        }
        int created = 0;
        int seen = 0;
        for (String id : ids) {
            if (seen++ >= properties.maxMessagesPerRun()) {
                // Leave the cursor where it was so the rest is read next run.
                LOGGER.warn("Gmail intake capped at {} messages this run", properties.maxMessagesPerRun());
                cursors.save(state);
                return created;
            }
            if (ingest(id)) {
                created++;
            }
        }
        state.setHistoryId(nextCursor, now());
        cursors.save(state);
        return created;
    }

    /** Ingests one message if it carries the label and was never claimed. */
    boolean ingest(String messageId) {
        if (ledger.existsById(messageId)) {
            return false;
        }
        GmailMessage message = GmailMessageParser.parse(gmail.message(messageId));
        if (!message.labelIds().contains(labelId())) {
            return false;
        }
        if (ledger.claim(messageId, now()) == 0) {
            return false;
        }
        GmailIngestedMessage row = ledger.findById(messageId).orElseThrow();
        if (message.body().isBlank()) {
            finish(row, Status.SKIPPED_EMPTY, null, null);
            return false;
        }
        Brief brief;
        try {
            Client client = resolveClient(message);
            if (client == null) {
                finish(row, Status.SKIPPED_UNKNOWN_SENDER, null, null);
                return false;
            }
            brief = briefService.ingest(properties.agencyId(), BriefChannel.EMAIL, client.getId(), payload(message));
        } catch (RuntimeException e) {
            finish(row, Status.FAILED, null, e.getClass().getSimpleName() + ": " + e.getMessage());
            LOGGER.warn("Gmail message {} could not become a brief: {}", messageId, e.toString());
            return false;
        }
        finish(row, Status.BRIEF_CREATED, brief.getId(), null);
        LOGGER.info("Gmail message {} became brief {}", messageId, brief.getId());
        if (properties.autoProcess()) {
            try {
                facade.processBrief(properties.agencyId(), brief.getId());
                finish(row, Status.PROCESSED, brief.getId(), null);
            } catch (RuntimeException e) {
                // The brief stays; it can be processed again from the UI.
                finish(row, Status.PROCESS_FAILED, brief.getId(), e.getClass().getSimpleName() + ": " + e.getMessage());
                LOGGER.warn("Brief {} from Gmail failed processing: {}", brief.getId(), e.toString());
            }
        }
        return true;
    }

    /** Starts or renews the push watch; Gmail drops a watch after 7 days. */
    public synchronized void renewWatch() {
        GmailClient.Watch watch = gmail.watch(properties.push().topic(), labelId());
        GmailSyncState state = cursors.findById(properties.mailbox())
                .orElseGet(() -> new GmailSyncState(properties.mailbox(), now()));
        if (state.getHistoryId() == null) {
            state.setHistoryId(watch.historyId(), now());
        }
        state.setWatchExpiresAt(watch.expiresAt(), now());
        cursors.save(state);
        LOGGER.info("Gmail watch on label {} renewed until {}", properties.label(), watch.expiresAt());
    }

    public String mailbox() {
        return properties.mailbox();
    }

    private Client resolveClient(GmailMessage message) {
        return clients.findByAgencyIdAndEmailHash(properties.agencyId(), blindIndex.hash(message.fromAddress()))
                .orElseGet(() -> properties.createUnknownSenders()
                        ? clientService.create(properties.agencyId(), message.fromName(), message.fromAddress(), null)
                        : null);
    }

    private static Map<String, Object> payload(GmailMessage message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", message.fromAddress());
        payload.put("subject", message.subject());
        payload.put("body", message.body());
        payload.put("receivedAt", message.receivedAt() == null ? null : message.receivedAt().toString());
        payload.put("gmailMessageId", message.id());
        payload.put("messageIdHeader", message.messageIdHeader());
        payload.put("truncated", message.truncated());
        return payload;
    }

    private String labelId() {
        if (labelId == null) {
            labelId = gmail.labelId(properties.label());
        }
        return labelId;
    }

    private void finish(GmailIngestedMessage row, Status status, Long briefId, String error) {
        row.mark(status, briefId, error, now());
        ledger.save(row);
    }

    private Instant now() {
        return clock.instant();
    }
}
