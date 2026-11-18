package com.cotizaia.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Spec 15 over HTTP: "vista" tracking and "Proyecto activado". Real tokens from
 * /api/auth/register, offline stub LLM, and no @Transactional (the facade
 * commits its own transactions, so every test registers its own agency).
 * Asserts exact values, not just status codes.
 */
@SpringBootTest(properties = "llm.provider=stub")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ViewedTrackingFlowTests {

    private static final String RESTAURANT_BRIEF = "Hola, tengo un restaurante y necesito una pagina web donde los "
            + "clientes vean el menu y puedan hacer reservas en linea. ¿Cuanto me sale?";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void viewedSetsTheTimeOnceAndDoesNotChangeStatus() throws Exception {
        Agency agency = setUpAgency();
        long id = quote(agency);
        String base = "/api/proposals/" + id;

        // Not allowed while still IN_REVIEW -> 409.
        call(post(base + "/viewed"), agency.token(), null, status().isConflict());

        approveAndSend(agency, id);

        // Before any view: no tracked time and the project is not active yet.
        call(get(base), agency.token(), null, status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.viewedAt").doesNotExist())
                .andExpect(jsonPath("$.projectActive").value(false));

        // First view: a tracked ISO timestamp is set exactly once, status stays SENT.
        MvcResult first = call(post(base + "/viewed"), agency.token(), null, status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.viewedAt").isNotEmpty())
                .andReturn();
        String viewedAt = JsonPath.read(first.getResponse().getContentAsString(), "$.viewedAt");
        // A parseable ISO-8601 instant proves the field is a timestamp, not an encrypted blob.
        assertThat(Instant.parse(viewedAt)).isBeforeOrEqualTo(Instant.now());

        // Second view is idempotent: the original timestamp is kept, status unchanged.
        call(post(base + "/viewed"), agency.token(), null, status().isOk())
                .andExpect(jsonPath("$.viewedAt").value(viewedAt))
                .andExpect(jsonPath("$.status").value("SENT"));

        // The fact is persisted (a fresh request/transaction reads the same value).
        call(get(base), agency.token(), null, status().isOk())
                .andExpect(jsonPath("$.viewedAt").value(viewedAt))
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.projectActive").value(false));
    }

    @Test
    void viewedIsAgencyScopedAndAlsoAllowedInNegotiating() throws Exception {
        Agency owner = setUpAgency();
        Agency intruder = setUpAgency();
        long id = quote(owner);
        String base = "/api/proposals/" + id;

        approveAndSend(owner, id);
        call(post(base + "/negotiate"), owner.token(), null, status().isOk())
                .andExpect(jsonPath("$.status").value("NEGOTIATING"));

        // Allowed in NEGOTIATING too, still without changing the status.
        call(post(base + "/viewed"), owner.token(), null, status().isOk())
                .andExpect(jsonPath("$.status").value("NEGOTIATING"))
                .andExpect(jsonPath("$.viewedAt").isNotEmpty());

        // Agency scoped: another agency sees a 404, not a 409 or a 200.
        call(post(base + "/viewed"), intruder.token(), null, status().isNotFound());
    }

    @Test
    void projectActiveIsFalseBeforeAcceptAndTrueAfter() throws Exception {
        Agency agency = setUpAgency();
        long id = quote(agency);
        String base = "/api/proposals/" + id;
        approveAndSend(agency, id);

        // Before accept: no issued contract + no deposit -> false.
        call(get(base), agency.token(), null, status().isOk())
                .andExpect(jsonPath("$.projectActive").value(false))
                .andExpect(jsonPath("$.deposit").doesNotExist());

        // A successful accept issues the contract and records the deposit.
        call(post(base + "/accept"), agency.token(), null, status().isOk())
                .andExpect(jsonPath("$.proposalStatus").value("CONTRACT_ISSUED"))
                .andExpect(jsonPath("$.contractStatus").value("ISSUED"))
                .andExpect(jsonPath("$.projectActive").value(true));

        // The derived flag is also true on the detail projection afterwards.
        call(get(base), agency.token(), null, status().isOk())
                .andExpect(jsonPath("$.projectActive").value(true))
                .andExpect(jsonPath("$.deposit.simulated").value(true));
    }

    @Test
    void scheduleMilestonesAreDerivedInTheDetailResponse() throws Exception {
        Agency agency = setUpAgency();
        long id = quote(agency);

        MvcResult detail = call(get("/api/proposals/" + id), agency.token(), null, status().isOk())
                .andExpect(jsonPath("$.schedule.milestones").isArray())
                .andExpect(jsonPath("$.schedule.milestones.length()").value(5))
                .andReturn();
        String json = detail.getResponse().getContentAsString();
        List<Map<String, Object>> phases = JsonPath.read(json, "$.schedule.phases");
        List<Map<String, Object>> milestones = JsonPath.read(json, "$.schedule.milestones");

        // The pipeline builds four phases: one "Entrega" per phase at its endWeek,
        // plus a final "Lanzamiento" at the last (latest) end week.
        assertThat(phases).hasSize(4);
        assertThat(milestones).hasSize(phases.size() + 1);
        for (int i = 0; i < phases.size(); i++) {
            assertThat(milestones.get(i).get("name")).isEqualTo("Entrega: " + phases.get(i).get("name"));
            assertThat(milestones.get(i).get("week")).isEqualTo(phases.get(i).get("endWeek"));
        }
        Map<String, Object> launch = milestones.get(phases.size());
        assertThat(launch.get("name")).isEqualTo("Lanzamiento");
        int lastEndWeek = phases.stream().mapToInt(p -> ((Number) p.get("endWeek")).intValue()).max().orElseThrow();
        assertThat(launch.get("week")).isEqualTo(lastEndWeek);
        assertThat((String) milestones.get(0).get("name")).startsWith("Entrega: ");
    }

    /** Registers an agency and configures client, catalog, roles and rates, as an owner would. */
    private Agency setUpAgency() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MvcResult registered = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agencyName\":\"Agencia " + suffix + "\",\"ownerFullName\":\"Owner\","
                                + "\"email\":\"owner-" + suffix + "@demo.co\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String token = AuthCookieTests.cookie(registered, "cz_access");

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

    private long quote(Agency agency) throws Exception {
        return readLong(call(post("/api/quotations"), agency.token(), brief(agency.clientId()), status().isCreated())
                .andExpect(jsonPath("$.proposalStatus").value("IN_REVIEW")).andReturn(), "$.proposalId");
    }

    private void approveAndSend(Agency agency, long id) throws Exception {
        String base = "/api/proposals/" + id;
        call(post(base + "/approve"), agency.token(), null, status().isOk());
        call(post(base + "/send"), agency.token(), null, status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"));
    }

    private String brief(long clientId) {
        return "{\"source\":\"WEB_FORM\",\"clientId\":" + clientId + ",\"payload\":{\"description\":\""
                + RESTAURANT_BRIEF + "\"}}";
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String token, String json,
            ResultMatcher expected) throws Exception {
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

    private record Agency(String token, long clientId) {
    }
}
