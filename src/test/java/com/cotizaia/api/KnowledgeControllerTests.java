package com.cotizaia.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Spec 03 part C over HTTP: a past proposal PDF becomes agency memory (grounded line items + passages), search
 * returns the closest past item with its approved hours and a citation, and nothing crosses agencies.
 */
@SpringBootTest(properties = {"llm.provider=stub", "knowledge.vector-store=jdbc"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class KnowledgeControllerTests {

    private static final Path SEED = Path.of("seed");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void uploadedProposalBecomesGroundedItemsMatchingTheApprovedHours() throws Exception {
        String token = agencyWithSeedCatalog();
        MvcResult uploaded = upload(token, "P01").andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("P01-propuesta.pdf"))
                .andExpect(jsonPath("$.passages").isNumber())
                .andExpect(jsonPath("$.rejectedItems").value(0))
                .andReturn();

        JsonNode body = mapper.readTree(uploaded.getResponse().getContentAsString());
        assertThat(body.path("passages").asInt()).isPositive();
        JsonNode expected = manifestProject("P01").path("items");
        assertThat(body.path("items")).hasSize(expected.size());
        for (int i = 0; i < expected.size(); i++) {
            assertThat(body.path("items").get(i).path("requirementType").asText())
                    .isEqualTo(expected.get(i).path("requirementType").asText());
            assertThat(body.path("items").get(i).path("hours").decimalValue())
                    .isEqualByComparingTo(expected.get(i).path("approvedHours").decimalValue());
            assertThat(body.path("items").get(i).path("page").asInt()).isEqualTo(1);
        }

        call(get("/api/knowledge/documents"), token).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("P01-propuesta.pdf"))
                .andExpect(jsonPath("$[0].kind").value("PROPOSAL_PDF"))
                .andExpect(jsonPath("$[0].items").value(expected.size()));
    }

    @Test
    void searchReturnsTheClosestPastItemWithItsHoursAndCitation() throws Exception {
        String token = agencyWithSeedCatalog();
        // Four past online shops of 150, 300, 40 and 600 products: the query must rank the 600-product one first.
        for (String project : List.of("P01", "P08", "P16", "P21")) {
            upload(token, project).andExpect(status().isCreated());
        }
        MvcResult result = call(get("/api/knowledge/search").param("q", "tienda para 600 productos con migracion del catalogo desde Excel")
                .param("type", "Tienda en linea").param("k", "3"), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentName").value("P21-propuesta.pdf"))
                .andExpect(jsonPath("$[0].requirementType").value("Tienda en linea"))
                .andExpect(jsonPath("$[0].hours").value(89))
                .andExpect(jsonPath("$[0].text").isNotEmpty())
                .andExpect(jsonPath("$[0].page").value(1))
                .andReturn();
        List<Double> similarities = JsonPath.read(result.getResponse().getContentAsString(), "$[*].similarity");
        assertThat(similarities).isSortedAccordingTo((a, b) -> Double.compare(b, a));
        assertThat(similarities).hasSize(3);
        List<String> types = JsonPath.read(result.getResponse().getContentAsString(), "$[*].requirementType");
        assertThat(types).containsOnly("Tienda en linea");
    }

    @Test
    void memoryNeverCrossesAgencies() throws Exception {
        String owner = agencyWithSeedCatalog();
        String intruder = agencyWithSeedCatalog();
        long documentId = readLong(upload(owner, "P01").andExpect(status().isCreated()).andReturn(), "$.documentId");

        call(get("/api/knowledge/documents"), intruder).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        call(get("/api/knowledge/search").param("q", "tienda con 150 productos"), intruder)
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        call(delete("/api/knowledge/documents/" + documentId), intruder).andExpect(status().isNotFound());

        call(delete("/api/knowledge/documents/" + documentId), owner).andExpect(status().isNoContent());
        call(get("/api/knowledge/search").param("q", "tienda con 150 productos"), owner)
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        call(get("/api/knowledge/documents"), owner).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void rejectsNonPdfUploadsAndBadSearchParameters() throws Exception {
        String token = agencyWithSeedCatalog();
        mockMvc.perform(multipart("/api/knowledge/documents")
                        .file(new MockMultipartFile("file", "notes.txt", MediaType.TEXT_PLAIN_VALUE,
                                "Tienda en linea 50 h".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        call(get("/api/knowledge/search").param("q", " "), token).andExpect(status().isBadRequest());
        call(get("/api/knowledge/search").param("q", "tienda").param("k", "500"), token)
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/knowledge/documents")).andExpect(status().isUnauthorized());
    }

    @Test
    void passageAndDocumentTextAreEncryptedAtRest() throws Exception {
        String token = agencyWithSeedCatalog();
        upload(token, "P01").andExpect(status().isCreated());
        String clientName = mapper.readTree(Files.readString(SEED.resolve("content/P01.json")))
                .path("clientName").asText();
        for (String sql : List.of("select text from knowledge_chunks", "select name from knowledge_documents")) {
            assertThat(jdbc.queryForList(sql, String.class)).noneMatch(value -> value.contains(clientName)
                    || value.contains("150 productos") || value.contains("P01-propuesta"));
        }
    }

    private ResultActions upload(String token, String project) throws Exception {
        byte[] pdf = Files.readAllBytes(SEED.resolve("pdf/train/" + project + "-propuesta.pdf"));
        return mockMvc.perform(multipart("/api/knowledge/documents")
                .file(new MockMultipartFile("file", project + "-propuesta.pdf", MediaType.APPLICATION_PDF_VALUE, pdf))
                .header("Authorization", "Bearer " + token));
    }

    /** Registers an agency and loads the seed agency's catalog (names + keywords + fixed hours). */
    private String agencyWithSeedCatalog() throws Exception {
        String email = "kb-" + UUID.randomUUID().toString().substring(0, 8) + "@demo.co";
        MvcResult registered = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agencyName\":\"Agencia KB\",\"ownerFullName\":\"Owner\",\"email\":\"" + email
                                + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated()).andReturn();
        String token = AuthCookieTests.cookie(registered, "cz_access");
        long catalogId = readLong(call(post("/api/catalog").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Servicios digitales\"}"), token).andExpect(status().isCreated()).andReturn(), "$.id");
        for (JsonNode entry : manifest().path("catalog")) {
            String json = mapper.createObjectNode().put("name", entry.path("name").asText())
                    .put("description", entry.path("keywords").asText())
                    .put("estimatedHours", entry.path("catalogHours").asInt()).toString();
            call(post("/api/catalog/" + catalogId + "/requirement-types").contentType(MediaType.APPLICATION_JSON)
                    .content(json), token).andExpect(status().isCreated());
        }
        return token;
    }

    private JsonNode manifest() throws Exception {
        return mapper.readTree(Files.readString(SEED.resolve("manifest.json")));
    }

    private JsonNode manifestProject(String id) throws Exception {
        for (JsonNode project : manifest().path("projects")) {
            if (id.equals(project.path("projectId").asText())) {
                return project;
            }
        }
        throw new AssertionError("no project " + id);
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private long readLong(MvcResult result, String path) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return value.longValue();
    }
}
