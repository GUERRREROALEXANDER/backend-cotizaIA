package com.cotizaia.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cotizaia.domain.UrgencyDecorator;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Spec 14 over HTTP: persisted price extras built with the real decorators.
 * Every monetary assertion compares exact BigDecimal values, the PDFs must be
 * regenerated, and the whole operation is undoable through the command history.
 */
@SpringBootTest(properties = "llm.provider=stub")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ProposalExtrasFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void urgencyChangesTheTotalByExactlyTwentyFivePercentAndRegeneratesThePdf() throws Exception {
        Session session = session();
        long proposal = session.quote();
        BigDecimal baseTotal = session.total(proposal);
        byte[] pdfBefore = session.pdf(proposal);

        MvcResult added = session.addExtra(proposal, "URGENCY").andExpect(status().isOk())
                .andExpect(jsonPath("$.extras.length()").value(1))
                .andExpect(jsonPath("$.extras[0].type").value("URGENCY"))
                .andExpect(jsonPath("$.extras[0].percentOrFixed").value(0.25))
                .andReturn();
        BigDecimal amount = session.decimal(added, "$.extras[0].amount");
        BigDecimal total = session.decimal(added, "$.total");

        assertThat(amount).isEqualByComparingTo(baseTotal.multiply(new BigDecimal("0.25")));
        assertThat(total).isEqualByComparingTo(baseTotal.multiply(new BigDecimal("1.25")));
        assertThat(session.pdf(proposal)).isNotEqualTo(pdfBefore);

        MvcResult removed = session.removeExtra(proposal, session.extraId(proposal)).andExpect(status().isOk())
                .andExpect(jsonPath("$.extras.length()").value(0))
                .andReturn();
        assertThat(session.decimal(removed, "$.total")).isEqualByComparingTo(baseTotal);
    }

    @Test
    void discountTakesExactlyTenPercentAndCompoundsOnTheRunningTotal() throws Exception {
        Session session = session();
        long proposal = session.quote();
        BigDecimal baseTotal = session.total(proposal);

        session.addExtra(proposal, "URGENCY").andExpect(status().isOk());
        MvcResult discounted = session.addExtra(proposal, "DISCOUNT").andExpect(status().isOk())
                .andExpect(jsonPath("$.extras[1].type").value("DISCOUNT"))
                .andExpect(jsonPath("$.extras[1].percentOrFixed").value(0.10))
                .andReturn();
        BigDecimal urgencyTotal = baseTotal.multiply(new BigDecimal("1.25"));
        BigDecimal discountAmount = session.decimal(discounted, "$.extras[1].amount");

        assertThat(discountAmount).isEqualByComparingTo(urgencyTotal.multiply(new BigDecimal("-0.10")));
        assertThat(session.decimal(discounted, "$.total"))
                .isEqualByComparingTo(urgencyTotal.multiply(new BigDecimal("0.90")));

        // Deleting the urgency leaves the subtotal plus the persisted discount delta only.
        long urgencyExtraId = session.extraIdByType(proposal, UrgencyDecorator.TYPE);
        MvcResult withoutUrgency = session.removeExtra(proposal, urgencyExtraId).andExpect(status().isOk())
                .andExpect(jsonPath("$.extras.length()").value(1))
                .andReturn();
        assertThat(session.decimal(withoutUrgency, "$.total"))
                .isEqualByComparingTo(baseTotal.add(discountAmount));
    }

    @Test
    void extendedWarrantyAddsItsFlatFeeExactly() throws Exception {
        Session session = session();
        long proposal = session.quote();
        BigDecimal baseTotal = session.total(proposal);

        MvcResult added = session.addExtra(proposal, "EXTENDED_WARRANTY").andExpect(status().isOk())
                .andExpect(jsonPath("$.extras[0].type").value("EXTENDED_WARRANTY"))
                .andReturn();
        BigDecimal amount = session.decimal(added, "$.extras[0].amount");

        assertThat(amount).isEqualByComparingTo(ExtendedWarrantyFee.FEE);
        assertThat(session.decimal(added, "$.total")).isEqualByComparingTo(baseTotal.add(ExtendedWarrantyFee.FEE));
    }

    @Test
    void theSameTypeTwiceIsAConflictAndLeavesTheProposalUntouched() throws Exception {
        Session session = session();
        long proposal = session.quote();
        MvcResult added = session.addExtra(proposal, "URGENCY").andExpect(status().isOk()).andReturn();
        BigDecimal withUrgency = session.decimal(added, "$.total");

        session.addExtra(proposal, "URGENCY").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Extra already applied: URGENCY"));
        MvcResult untouched = session.call(get("/api/proposals/" + proposal)).andExpect(status().isOk())
                .andExpect(jsonPath("$.extras.length()").value(1))
                .andReturn();
        assertThat(session.decimal(untouched, "$.total")).isEqualByComparingTo(withUrgency);
    }

    @Test
    void unknownTypeIsABadRequest() throws Exception {
        Session session = session();
        long proposal = session.quote();
        session.addExtra(proposal, "FREIGHT").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unknown extra type: FREIGHT"));
        session.call(get("/api/proposals/" + proposal)).andExpect(jsonPath("$.extras.length()").value(0));
    }

    @Test
    void extrasAreRejectedOnceApprovedAndInOtherStatuses() throws Exception {
        Session session = session();
        long proposal = session.quote();
        session.addExtra(proposal, "URGENCY").andExpect(status().isOk());

        session.call(post("/api/proposals/" + proposal + "/approve")).andExpect(status().isOk());
        session.addExtra(proposal, "DISCOUNT").andExpect(status().isConflict());
        session.removeExtra(proposal, session.extraId(proposal)).andExpect(status().isConflict());
        session.call(get("/api/proposals/" + proposal)).andExpect(jsonPath("$.extras.length()").value(1));

        // A proposal outside the review window (here: sent after approval) is also a 409.
        long sent = session.quote();
        session.call(post("/api/proposals/" + sent + "/approve")).andExpect(status().isOk());
        session.call(post("/api/proposals/" + sent + "/send")).andExpect(status().isOk());
        session.addExtra(sent, "URGENCY").andExpect(status().isConflict());
    }

    @Test
    void foreignAgencyAndUnknownIdsAreNotFound() throws Exception {
        Session session = session();
        long proposal = session.quote();
        Session intruder = session();

        intruder.addExtra(proposal, "URGENCY").andExpect(status().isNotFound());
        intruder.removeExtra(proposal, 1L).andExpect(status().isNotFound());
        session.call(post("/api/proposals/999999/extras").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"URGENCY\"}")).andExpect(status().isNotFound());
        session.removeExtra(proposal, 999999).andExpect(status().isNotFound());
    }

    @Test
    void addingAndRemovingAnExtraIsUndoableThroughTheCommandHistory() throws Exception {
        Session session = session();
        long proposal = session.quote();
        BigDecimal baseTotal = session.total(proposal);

        session.addExtra(proposal, "URGENCY").andExpect(status().isOk());
        BigDecimal withUrgency = session.total(proposal);
        long extraId = session.extraId(proposal);
        session.removeExtra(proposal, extraId).andExpect(status().isOk());
        assertThat(session.total(proposal)).isEqualByComparingTo(baseTotal);

        // Undo the removal: the extra comes back with its exact stored delta.
        session.call(post("/api/proposals/" + proposal + "/undo")).andExpect(status().isOk())
                .andExpect(jsonPath("$.extras.length()").value(1))
                .andExpect(jsonPath("$.extras[0].type").value("URGENCY"));
        assertThat(session.total(proposal)).isEqualByComparingTo(withUrgency);

        // Undo again: the ADD_EXTRA record reverses, dropping the re-added extra.
        session.call(post("/api/proposals/" + proposal + "/undo")).andExpect(status().isOk())
                .andExpect(jsonPath("$.extras.length()").value(0));
        assertThat(session.total(proposal)).isEqualByComparingTo(baseTotal);

        session.call(get("/api/proposals/" + proposal + "/commands")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("ADD_EXTRA"))
                .andExpect(jsonPath("$[1].type").value("REMOVE_EXTRA"))
                .andExpect(jsonPath("$[0].undoneAt").isNotEmpty())
                .andExpect(jsonPath("$[1].undoneAt").isNotEmpty());
    }

    private Session session() throws Exception {
        String email = "extras-" + UUID.randomUUID().toString().substring(0, 8) + "@demo.co";
        MvcResult registered = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agencyName\":\"Agencia Extras\",\"ownerFullName\":\"Owner\",\"email\":\"" + email
                                + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated()).andReturn();
        Session session = new Session(mockMvc, AuthCookieTests.cookie(registered, "cz_access"));
        session.setUp();
        return session;
    }

    /** Copied from ExtendedWarrantyDecorator usage so the fee lives in one place per side of the test. */
    private static final class ExtendedWarrantyFee {

        private static final BigDecimal FEE = new BigDecimal("500000.00");
    }

    static final class Session {

        private final MockMvc mockMvc;

        private final String token;

        private long clientId;

        Session(MockMvc mockMvc, String token) {
            this.mockMvc = mockMvc;
            this.token = token;
        }

        void setUp() throws Exception {
            clientId = id(call(post("/api/clients").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Restaurante\"}")).andExpect(status().isCreated()).andReturn());
            long catalogId = id(call(post("/api/catalog").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Web\"}")).andExpect(status().isCreated()).andReturn());
            call(post("/api/catalog/" + catalogId + "/requirement-types").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Sistema de reservas\",\"description\":\"reserva, reservas, agenda\","
                            + "\"estimatedHours\":32}")).andExpect(status().isCreated());
            long roleId = id(call(post("/api/roles").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Developer\"}")).andExpect(status().isCreated()).andReturn());
            call(post("/api/roles/" + roleId + "/rates").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"copPerHour\":100000}")).andExpect(status().isCreated());
        }

        long quote() throws Exception {
            MvcResult quoted = call(post("/api/quotations").contentType(MediaType.APPLICATION_JSON).content(
                    "{\"source\":\"WEB_FORM\",\"clientId\":" + clientId + ",\"payload\":{\"description\":"
                            + "\"Tengo un restaurante y necesito un sistema de reservas en linea.\"}}"))
                    .andExpect(status().isCreated()).andReturn();
            Number proposal = JsonPath.read(quoted.getResponse().getContentAsString(), "$.proposalId");
            return proposal.longValue();
        }

        ResultActions addExtra(long proposalId, String type) throws Exception {
            return call(post("/api/proposals/" + proposalId + "/extras").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"type\":\"" + type + "\"}"));
        }

        ResultActions removeExtra(long proposalId, long extraId) throws Exception {
            return call(delete("/api/proposals/" + proposalId + "/extras/" + extraId));
        }

        BigDecimal total(long proposalId) throws Exception {
            return decimal(call(get("/api/proposals/" + proposalId)).andExpect(status().isOk()).andReturn(),
                    "$.total");
        }

        long extraId(long proposalId) throws Exception {
            MvcResult detail = call(get("/api/proposals/" + proposalId)).andExpect(status().isOk()).andReturn();
            return decimal(detail, "$.extras[0].id").longValue();
        }

        long extraIdByType(long proposalId, String type) throws Exception {
            MvcResult detail = call(get("/api/proposals/" + proposalId)).andExpect(status().isOk()).andReturn();
            java.util.List<Number> matched = JsonPath.read(detail.getResponse().getContentAsString(),
                    "$.extras[?(@.type == '" + type + "')].id");
            return matched.get(0).longValue();
        }

        byte[] pdf(long proposalId) throws Exception {
            return call(get("/api/proposals/" + proposalId + "/documents/PROPOSAL"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        }

        BigDecimal decimal(MvcResult result, String path) throws Exception {
            Number value = JsonPath.read(result.getResponse().getContentAsString(), path);
            return new BigDecimal(value.toString());
        }

        ResultActions call(MockHttpServletRequestBuilder request) throws Exception {
            return mockMvc.perform(request.header("Authorization", "Bearer " + token));
        }

        private long id(MvcResult result) throws Exception {
            Number value = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
            return value.longValue();
        }
    }
}
