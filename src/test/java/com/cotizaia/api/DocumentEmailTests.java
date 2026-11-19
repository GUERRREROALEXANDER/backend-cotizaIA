package com.cotizaia.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cotizaia.mail.EmailGateway;
import com.cotizaia.mail.EmailMessage;
import com.cotizaia.mail.MailProperties;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Spec 16 documents: sending a proposal mails its PDF and accepting it mails the contract with the
 * deposit and its reference. Recipient is the authenticated user who performed the action, the
 * client only with MAIL_SEND_TO_CLIENT, nothing carries a session token, and a broken transport
 * never turns the HTTP call into an error.
 *
 * <p>The gateway is a capturing stub here (the mail plumbing itself is proven against
 * GreenMail in {@link AuthEmailVerificationRequiredTests}), which is what lets one test make the
 * transport fail on purpose.</p>
 */
@SpringBootTest(properties = {"llm.provider=stub", "mail.enabled=true", "mail.send-to-client=false"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class DocumentEmailTests {

    private static final String BRIEF = "Hola, tengo un restaurante y necesito una pagina web donde los clientes "
            + "vean el menu y puedan hacer reservas en linea. ¿Cuanto me sale?";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MailProperties mailProperties;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private EmailGateway gateway;

    @Test
    void sendingMailsTheProposalPdfToTheActingUser() throws Exception {
        Agency agency = setUpAgency();
        long proposalId = approvedProposal(agency);
        clearInvocations(gateway);

        call(post("/api/proposals/" + proposalId + "/send"), agency.token(), null, status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"));

        List<EmailMessage> mails = sentTo(agency.ownerEmail());
        assertThat(mails).hasSize(1);
        EmailMessage mail = mails.get(0);
        assertThat(mail.subject()).isEqualTo("Propuesta enviada: PDF adjunto");
        assertThat(mail.text()).contains("Restaurante El Sabor");

        byte[] downloaded = downloadedPdf(agency, proposalId, "PROPOSAL");
        assertThat(mail.attachments()).hasSize(1);
        assertThat(mail.attachments().get(0).name()).isEqualTo("propuesta-" + proposalId + ".pdf");
        assertThat(mail.attachments().get(0).contentType()).isEqualTo("application/pdf");
        assertThat(mail.attachments().get(0).bytes()).isEqualTo(downloaded);
        assertThat(new String(downloaded, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");

        // The body is what the user already sees and never a credential.
        assertThat(mail.text()).doesNotContain(agency.token()).doesNotContain("Bearer").doesNotContain("eyJ");
        // The client only gets a copy when the deployment asks for it.
        assertThat(sentTo(agency.clientEmail())).isEmpty();
    }

    @Test
    void acceptingMailsTheContractWithDepositAndReference() throws Exception {
        Agency agency = setUpAgency();
        long proposalId = approvedProposal(agency);
        call(post("/api/proposals/" + proposalId + "/send"), agency.token(), null, status().isOk());
        clearInvocations(gateway);

        call(post("/api/proposals/" + proposalId + "/accept"), agency.token(), null, status().isOk())
                .andExpect(jsonPath("$.proposalStatus").value("CONTRACT_ISSUED"))
                .andExpect(jsonPath("$.depositAmount").isNumber())
                .andExpect(jsonPath("$.providerRef").isNotEmpty());

        // The receipt facts in the mail are the ones the deposit really recorded.
        BigDecimal deposit = jdbc.queryForObject(
                "SELECT amount FROM payments WHERE proposal_id = ?", BigDecimal.class, proposalId);
        String reference = jdbc.queryForObject(
                "SELECT provider_ref FROM payments WHERE proposal_id = ?", String.class, proposalId);

        List<EmailMessage> mails = sentTo(agency.ownerEmail());
        assertThat(mails).hasSize(1);
        EmailMessage mail = mails.get(0);
        assertThat(mail.subject()).isEqualTo("Contrato emitido: PDF adjunto");
        assertThat(mail.text()).contains(deposit.toPlainString());
        assertThat(mail.text()).contains(reference);
        assertThat(mail.attachments()).hasSize(1);
        assertThat(mail.attachments().get(0).name()).isEqualTo("contrato-" + proposalId + ".pdf");
        assertThat(mail.attachments().get(0).bytes())
                .isEqualTo(downloadedPdf(agency, proposalId, "CONTRACT"));
    }

    @Test
    void clientReceivesACopyOnlyWhenTheFlagIsOn() throws Exception {
        Agency agency = setUpAgency();
        long proposalId = approvedProposal(agency);
        clearInvocations(gateway);

        mailProperties.setSendToClient(true);
        try {
            call(post("/api/proposals/" + proposalId + "/send"), agency.token(), null, status().isOk());
        } finally {
            mailProperties.setSendToClient(false);
        }

        assertThat(sentTo(agency.ownerEmail())).hasSize(1);
        List<EmailMessage> clientCopy = sentTo(agency.clientEmail());
        assertThat(clientCopy).hasSize(1);
        assertThat(clientCopy.get(0).attachments().get(0).name()).isEqualTo("propuesta-" + proposalId + ".pdf");
    }

    @Test
    void failingSmtpNeverFailsTheHttpCall() throws Exception {
        Agency agency = setUpAgency();
        long proposalId = approvedProposal(agency);
        doThrow(new MailSendException("SMTP is down")).when(gateway).send(any());

        call(post("/api/proposals/" + proposalId + "/send"), agency.token(), null, status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"));
        call(get("/api/proposals/" + proposalId), agency.token(), null, status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"));

        call(post("/api/proposals/" + proposalId + "/accept"), agency.token(), null, status().isOk())
                .andExpect(jsonPath("$.proposalStatus").value("CONTRACT_ISSUED"));

        verify(gateway, atLeastOnce()).send(any());
    }

    private List<EmailMessage> sentTo(String address) {
        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(gateway, atLeastOnce()).send(captor.capture());
        return captor.getAllValues().stream().filter(message -> message.to().equals(address)).toList();
    }

    private byte[] downloadedPdf(Agency agency, long proposalId, String type) throws Exception {
        MvcResult pdf = call(get("/api/proposals/" + proposalId + "/documents/" + type), agency.token(), null,
                status().isOk()).andReturn();
        return pdf.getResponse().getContentAsByteArray();
    }

    private long approvedProposal(Agency agency) throws Exception {
        String brief = "{\"source\":\"WEB_FORM\",\"clientId\":" + agency.clientId() + ",\"payload\":{\"description\":\""
                + BRIEF + "\"}}";
        long proposalId = readLong(call(post("/api/quotations"), agency.token(), brief, status().isCreated())
                .andReturn(), "$.proposalId");
        call(post("/api/proposals/" + proposalId + "/approve"), agency.token(), null, status().isOk());
        return proposalId;
    }

    /** Registers an agency and configures the client, catalog, roles and rates a quote needs. */
    private Agency setUpAgency() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String ownerEmail = "owner-mail-" + suffix + "@demo.co";
        String clientEmail = "sabor-" + suffix + "@demo.co";
        MvcResult registered = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agencyName\":\"Agencia " + suffix + "\",\"ownerFullName\":\"Owner\","
                                + "\"email\":\"" + ownerEmail + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated()).andReturn();
        String token = AuthCookieTests.cookie(registered, "cz_access");
        clearInvocations(gateway);

        long clientId = readLong(call(post("/api/clients"), token,
                "{\"name\":\"Restaurante El Sabor\",\"email\":\"" + clientEmail + "\"}",
                status().isCreated()).andReturn(), "$.id");
        long catalogId = readLong(call(post("/api/catalog"), token, "{\"name\":\"Desarrollo web\"}",
                status().isCreated()).andReturn(), "$.id");
        addRequirementType(token, catalogId, "Diseno responsive", "pagina, web, sitio", 24);
        addRequirementType(token, catalogId, "Menu digital", "menu, carta", 12);
        addRequirementType(token, catalogId, "Sistema de reservas", "reserva, reservas, agenda", 32);
        long roleId = readLong(call(post("/api/roles"), token, "{\"name\":\"Developer\"}", status().isCreated())
                .andReturn(), "$.id");
        call(post("/api/roles/" + roleId + "/rates"), token, "{\"copPerHour\":120000}", status().isCreated());
        clearInvocations(gateway);
        return new Agency(token, clientId, ownerEmail, clientEmail);
    }

    private void addRequirementType(String token, long catalogId, String name, String keywords, int hours)
            throws Exception {
        call(post("/api/catalog/" + catalogId + "/requirement-types"), token,
                "{\"name\":\"" + name + "\",\"description\":\"" + keywords + "\",\"estimatedHours\":" + hours + "}",
                status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions call(MockHttpServletRequestBuilder request,
            String token, String json, ResultMatcher expected) throws Exception {
        request.header("Authorization", "Bearer " + token);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request).andExpect(expected);
    }

    private long readLong(MvcResult result, String path) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return value.longValue();
    }

    private record Agency(String token, long clientId, String ownerEmail, String clientEmail) {
    }
}
