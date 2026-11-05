package com.cotizaia.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cotizaia.api.AuthCookieTests;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The judge of the whole RAG feature, end to end over the public API (spec 04, plan judge fixed 2026-11-05):
 *
 * <ol>
 *   <li>a fresh agency loads the seed catalog and uploads the 24 <b>train</b> proposal PDFs as memory;</li>
 *   <li>each of the 6 <b>holdout</b> client briefs goes through {@code POST /api/quotations} like a real one;</li>
 *   <li>for every holdout line item the quoted hours are compared with the hours a human approved.</li>
 * </ol>
 *
 * <p>Baseline = the agency's fixed catalog hours. A requirement the agent failed to extract counts with the catalog
 * value, so missing items never flatter the result. <b>Pass line: memory MAE &lt;= 0.70 x catalog MAE.</b>
 * Writes {@code target/eval/report.md}. Default family is the offline stub; run the real one with
 * {@code ./mvnw test -Dtest=EstimationEvalTests -Deval.provider=groq} (needs GROQ_API_KEY and VOYAGE_API_KEY).
 * The corpus is synthetic (see seed/README.md): this measures the mechanism, not a real agency.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class EstimationEvalTests {

    private static final Path SEED = Path.of("seed");

    private static final double PASS_RATIO = 0.70;

    @DynamicPropertySource
    static void provider(DynamicPropertyRegistry registry) {
        registry.add("llm.provider", () -> System.getProperty("eval.provider", "stub"));
        registry.add("knowledge.vector-store", () -> "jdbc");
        // With a rate-limited real provider the run outlasts the 15-minute access token; the eval is one long session.
        registry.add("security.jwt.ttl", () -> "PT2H");
    }

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void memoryBeatsTheFixedCatalogOnTheHoldoutBriefs() throws Exception {
        JsonNode manifest = mapper.readTree(Files.readString(SEED.resolve("manifest.json")));
        String token = agencyWithSeedCatalog(manifest);
        long clientId = id(call(post("/api/clients").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Cliente eval\"}"), token).andExpect(status().isCreated()).andReturn());

        int indexed = 0;
        for (JsonNode project : manifest.path("projects")) {
            if ("train".equals(project.path("split").asText())) {
                upload(token, project.path("projectId").asText());
                indexed++;
            }
        }
        assertThat(indexed).isEqualTo(24);

        List<Row> rows = new ArrayList<>();
        for (JsonNode project : manifest.path("projects")) {
            if (!"holdout".equals(project.path("split").asText())) {
                continue;
            }
            String pid = project.path("projectId").asText();
            String brief = mapper.readTree(Files.readString(SEED.resolve("content/" + pid + ".json")))
                    .path("brief").asText();
            Map<String, Quoted> quoted = quote(token, clientId, brief);
            for (JsonNode item : project.path("items")) {
                String type = item.path("requirementType").asText();
                BigDecimal truth = item.path("approvedHours").decimalValue();
                BigDecimal catalog = item.path("catalogHours").decimalValue();
                Quoted q = quoted.get(type);
                rows.add(new Row(pid, type, truth, catalog, q == null ? catalog : q.hours(),
                        q == null ? "NOT_EXTRACTED" : q.source(), q == null ? 0 : q.citations()));
            }
        }

        double catalogMae = rows.stream().mapToDouble(r -> r.truth().subtract(r.catalog()).abs().doubleValue())
                .average().orElseThrow();
        double agentMae = rows.stream().mapToDouble(r -> r.truth().subtract(r.predicted()).abs().doubleValue())
                .average().orElseThrow();
        writeReport(rows, catalogMae, agentMae);

        assertThat(rows).hasSize(13);
        assertThat(agentMae).as("agent MAE %.2f h vs catalog MAE %.2f h (pass <= %.2f h)", agentMae, catalogMae,
                catalogMae * PASS_RATIO).isLessThanOrEqualTo(catalogMae * PASS_RATIO);
    }

    private Map<String, Quoted> quote(String token, long clientId, String brief) throws Exception {
        ObjectNode payload = mapper.createObjectNode().put("description", brief);
        ObjectNode body = mapper.createObjectNode().put("source", "WEB_FORM").put("clientId", clientId);
        body.set("payload", payload);
        MvcResult quoted = call(post("/api/quotations").contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()), token).andReturn();
        Map<String, Quoted> result = new HashMap<>();
        if (quoted.getResponse().getStatus() != 201) {
            return result;
        }
        Number proposalId = JsonPath.read(quoted.getResponse().getContentAsString(), "$.proposalId");
        if (proposalId == null) {
            return result;
        }
        JsonNode detail = mapper.readTree(call(get("/api/proposals/" + proposalId.longValue()), token)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode item : detail.path("items")) {
            result.put(item.path("requirementType").asText(), new Quoted(item.path("hours").decimalValue(),
                    item.path("estimateSource").asText(), item.path("citations").size()));
        }
        return result;
    }

    private void writeReport(List<Row> rows, double catalogMae, double agentMae) throws Exception {
        StringBuilder report = new StringBuilder()
                .append("# Estimation eval (synthetic seed corpus)\n\n")
                .append("Provider family: `").append(System.getProperty("eval.provider", "stub")).append("`  \n")
                .append("Memory: 24 train proposals. Inputs: 6 holdout client briefs through `POST /api/quotations`.\n\n")
                .append(String.format(Locale.ROOT, "| | MAE (hours per item) |%n|---|---|%n| Fixed catalog | %.2f |%n"
                        + "| Agent (memory + fallback) | %.2f |%n| Pass line (0.70 x catalog) | %.2f |%n%n",
                        catalogMae, agentMae, catalogMae * PASS_RATIO))
                .append(String.format(Locale.ROOT, "Improvement: **%.0f%%** lower error than the catalog.%n%n",
                        100 * (1 - agentMae / catalogMae)))
                .append("| Project | Requirement | Approved | Catalog | Agent | Source | Citations |\n")
                .append("|---|---|---|---|---|---|---|\n");
        for (Row r : rows) {
            report.append("| ").append(r.project()).append(" | ").append(r.type()).append(" | ")
                    .append(r.truth().setScale(0, RoundingMode.HALF_UP)).append(" | ")
                    .append(r.catalog().setScale(0, RoundingMode.HALF_UP)).append(" | ")
                    .append(r.predicted().setScale(1, RoundingMode.HALF_UP)).append(" | ")
                    .append(r.source()).append(" | ").append(r.citations()).append(" |\n");
        }
        Path out = Path.of("target/eval/report.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report.toString());
    }

    private void upload(String token, String project) throws Exception {
        byte[] pdf = Files.readAllBytes(SEED.resolve("pdf/train/" + project + "-propuesta.pdf"));
        mockMvc.perform(multipart("/api/knowledge/documents")
                        .file(new MockMultipartFile("file", project + "-propuesta.pdf", MediaType.APPLICATION_PDF_VALUE,
                                pdf))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
    }

    private String agencyWithSeedCatalog(JsonNode manifest) throws Exception {
        String email = "eval-" + UUID.randomUUID().toString().substring(0, 8) + "@demo.co";
        MvcResult registered = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agencyName\":\"Agencia Eval\",\"ownerFullName\":\"Owner\",\"email\":\"" + email
                                + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated()).andReturn();
        String token = AuthCookieTests.cookie(registered, "cz_access");
        long catalogId = id(call(post("/api/catalog").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Servicios digitales\"}"), token).andExpect(status().isCreated()).andReturn());
        for (JsonNode entry : manifest.path("catalog")) {
            String json = mapper.createObjectNode().put("name", entry.path("name").asText())
                    .put("description", entry.path("keywords").asText())
                    .put("estimatedHours", entry.path("catalogHours").asInt()).toString();
            call(post("/api/catalog/" + catalogId + "/requirement-types").contentType(MediaType.APPLICATION_JSON)
                    .content(json), token).andExpect(status().isCreated());
        }
        long roleId = id(call(post("/api/roles").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Equipo\"}"), token).andExpect(status().isCreated()).andReturn());
        call(post("/api/roles/" + roleId + "/rates").contentType(MediaType.APPLICATION_JSON)
                .content("{\"copPerHour\":" + manifest.path("rateCopPerHour").asInt() + "}"), token)
                .andExpect(status().isCreated());
        return token;
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private long id(MvcResult result) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return value.longValue();
    }

    private record Quoted(BigDecimal hours, String source, int citations) {
    }

    private record Row(String project, String type, BigDecimal truth, BigDecimal catalog, BigDecimal predicted,
            String source, int citations) {
    }
}
