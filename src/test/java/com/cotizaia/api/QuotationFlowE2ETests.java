package com.cotizaia.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Issue #20 acceptance: the happy path ingest -> approve -> accept is a chain of 2xx calls through the HTTP API
 * only (real tokens from /api/auth/register, offline stub LLM), analytics are computed from the real rows, and the
 * error contract (401 / 404 cross-agency / 422 / 400) holds. Not @Transactional: the facade commits its own
 * transactions, so every test registers its own agency.
 */
@SpringBootTest(properties = "llm.provider=stub")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class QuotationFlowE2ETests {

    private static final String RESTAURANT_BRIEF = "Hola, tengo un restaurante y necesito una pagina web donde los "
            + "clientes vean el menu y puedan hacer reservas en linea. ¿Cuanto me sale?";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void quotationHappyPathIsAChainOfSuccessfulCalls() throws Exception {
        Agency agency = setUpAgency();

        MvcResult quoted = call(post("/api/quotations"), agency.token(), brief(agency.clientId(), RESTAURANT_BRIEF),
                status().isCreated())
                .andExpect(jsonPath("$.halted").value(false))
                .andExpect(jsonPath("$.proposalStatus").value("IN_REVIEW"))
                .andExpect(jsonPath("$.documents", hasSize(3)))
                .andExpect(jsonPath("$.questions", hasSize(2)))
                .andReturn();
        long proposalId = readLong(quoted, "$.proposalId");
        BigDecimal quotedTotal = readDecimal(quoted, "$.total");
        assertThat(quotedTotal).isPositive();

        call(get("/api/proposals/queue"), agency.token(), null, status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) proposalId)));

        MvcResult detail = call(get("/api/proposals/" + proposalId), agency.token(), null, status().isOk())
                .andReturn();
        long itemId = readLong(detail, "$.items[0].id");
        MvcResult adjusted = call(put("/api/proposals/" + proposalId + "/items/" + itemId), agency.token(),
                "{\"hours\":50}", status().isOk())
                .andReturn();
        BigDecimal adjustedTotal = readDecimal(adjusted, "$.total");
        assertThat(adjustedTotal).isNotEqualByComparingTo(quotedTotal);

        call(post("/api/proposals/" + proposalId + "/approve"), agency.token(), null, status().isOk())
                .andExpect(jsonPath("$.approvedAt").isNotEmpty());
        call(post("/api/proposals/" + proposalId + "/send"), agency.token(), null, status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"));
        MvcResult accepted = call(post("/api/proposals/" + proposalId + "/accept"), agency.token(), null,
                status().isOk())
                .andExpect(jsonPath("$.proposalStatus").value("CONTRACT_ISSUED"))
                .andExpect(jsonPath("$.simulated").value(true))
                .andReturn();
        BigDecimal deposit = readDecimal(accepted, "$.depositAmount");
        assertThat(deposit.multiply(BigDecimal.valueOf(2)).subtract(adjustedTotal).abs())
                .isLessThanOrEqualTo(new BigDecimal("0.01"));

        call(get("/api/proposals/" + proposalId), agency.token(), null, status().isOk())
                .andExpect(jsonPath("$.status").value("CONTRACT_ISSUED"))
                .andExpect(jsonPath("$.deposit.simulated").value(true));

        MvcResult pdf = call(get("/api/proposals/" + proposalId + "/documents/PROPOSAL"), agency.token(), null,
                status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn();
        assertThat(new String(pdf.getResponse().getContentAsByteArray(), 0, 4)).isEqualTo("%PDF");

        call(get("/api/analytics"), agency.token(), null, status().isOk())
                .andExpect(jsonPath("$.sentCount").value(1))
                .andExpect(jsonPath("$.acceptedCount").value(1))
                .andExpect(jsonPath("$.acceptanceRate").value(1.0))
                .andExpect(jsonPath("$.averageResponseTimeMinutes").isNumber());
        call(get("/api/notifications"), agency.token(), null, status().isOk())
                .andExpect(jsonPath("$").isNotEmpty());
    }

    @Test
    void errorContractHolds() throws Exception {
        Agency agency = setUpAgency();
        Agency other = setUpAgency();
        MvcResult quoted = call(post("/api/quotations"), agency.token(), brief(agency.clientId(), RESTAURANT_BRIEF),
                status().isCreated())
                .andReturn();
        long proposalId = readLong(quoted, "$.proposalId");
        long itemId = readLong(call(get("/api/proposals/" + proposalId), agency.token(), null, status().isOk())
                .andReturn(), "$.items[0].id");

        mockMvc.perform(get("/api/proposals/" + proposalId)).andExpect(status().isUnauthorized());
        call(get("/api/proposals/" + proposalId), other.token(), null, status().isNotFound());
        call(put("/api/proposals/" + proposalId + "/items/" + itemId), agency.token(), "{\"hours\":-1}",
                status().isBadRequest())
                .andExpect(jsonPath("$.fields.hours").exists());
        call(post("/api/clients"), agency.token(), "{\"name\":", status().isBadRequest());
        call(post("/api/proposals/" + proposalId + "/send"), agency.token(), null, status().isConflict());
        call(post("/api/proposals/" + proposalId + "/accept"), agency.token(), null,
                status().isUnprocessableEntity())
                .andExpect(jsonPath("$.from").value("IN_REVIEW"))
                .andExpect(jsonPath("$.to").value("ACCEPTED"));
    }

    @Test
    void haltedBriefIsCompletedByAnsweringItsBlockingQuestions() throws Exception {
        Agency agency = setUpAgency();
        MvcResult halted = call(post("/api/quotations"), agency.token(),
                brief(agency.clientId(), "hola, necesito ayuda con algo"), status().isCreated())
                .andExpect(jsonPath("$.halted").value(true))
                .andExpect(jsonPath("$.questions[*].code", hasItem("PROJECT_TYPE_UNKNOWN")))
                .andExpect(jsonPath("$.questions[*].code", hasItem("NO_SCOPE")))
                .andReturn();
        long briefId = readLong(halted, "$.briefId");

        // Each answer re-runs the chain; a clarification can also settle other open questions, which the re-run
        // removes, so always answer the next blocking question that is still OPEN in the latest result.
        MvcResult last = halted;
        for (int round = 0; round < 3; round++) {
            List<Integer> open = JsonPath.read(last.getResponse().getContentAsString(),
                    "$.questions[?(@.blocking == true && @.status == 'OPEN')].id");
            if (open.isEmpty()) {
                break;
            }
            last = call(post("/api/briefs/" + briefId + "/questions/" + open.get(0) + "/answer"), agency.token(),
                    "{\"answer\":\"Una pagina web con menu digital y sistema de reservas\"}", status().isOk())
                    .andReturn();
        }
        assertThat(JsonPath.<Boolean>read(last.getResponse().getContentAsString(), "$.halted")).isFalse();
        assertThat(JsonPath.<String>read(last.getResponse().getContentAsString(), "$.proposalStatus"))
                .isEqualTo("IN_REVIEW");

        call(get("/api/briefs/" + briefId), agency.token(), null, status().isOk())
                .andExpect(jsonPath("$.questions[?(@.status == 'RESOLVED')].answer",
                        hasItem("Una pagina web con menu digital y sistema de reservas")))
                .andExpect(jsonPath("$.questions[?(@.blocking == true && @.status == 'OPEN')]", hasSize(0)));
    }

    /** Registers an agency and configures it the way an owner would: client, catalog, roles and rates. */
    private Agency setUpAgency() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MvcResult registered = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agencyName\":\"Agencia " + suffix + "\",\"ownerFullName\":\"Owner\","
                                + "\"email\":\"owner-" + suffix + "@demo.co\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String token = JsonPath.read(registered.getResponse().getContentAsString(), "$.token");

        long clientId = readLong(call(post("/api/clients"), token,
                "{\"name\":\"Restaurante El Sabor\",\"email\":\"sabor-" + suffix + "@demo.co\"}",
                status().isCreated()).andReturn(), "$.id");
        long catalogId = readLong(call(post("/api/catalog"), token, "{\"name\":\"Desarrollo web\"}",
                status().isCreated()).andReturn(), "$.id");
        addRequirementType(token, catalogId, "Diseno responsive", "pagina, web, sitio", 24);
        addRequirementType(token, catalogId, "Menu digital", "menu, carta", 12);
        addRequirementType(token, catalogId, "Sistema de reservas", "reserva, reservas, agenda", 32);
        addRequirementType(token, catalogId, "Pasarela de pagos", "pago, pagos, checkout", 20);
        addRole(token, "Developer", 120000);
        addRole(token, "Designer", 80000);
        return new Agency(token, clientId);
    }

    private void addRequirementType(String token, long catalogId, String name, String keywords, int hours)
            throws Exception {
        call(post("/api/catalog/" + catalogId + "/requirement-types"), token,
                "{\"name\":\"" + name + "\",\"description\":\"" + keywords + "\",\"estimatedHours\":" + hours + "}",
                status().isCreated());
    }

    private void addRole(String token, String name, long copPerHour) throws Exception {
        long roleId = readLong(call(post("/api/roles"), token, "{\"name\":\"" + name + "\"}", status().isCreated())
                .andReturn(), "$.id");
        call(post("/api/roles/" + roleId + "/rates"), token, "{\"copPerHour\":" + copPerHour + "}",
                status().isCreated());
    }

    private String brief(long clientId, String text) {
        return "{\"source\":\"WEB_FORM\",\"clientId\":" + clientId + ",\"payload\":{\"description\":\"" + text
                + "\"}}";
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

    private BigDecimal readDecimal(MvcResult result, String path) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return new BigDecimal(value.toString());
    }

    private record Agency(String token, long clientId) {
    }
}
