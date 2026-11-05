package com.cotizaia.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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
 * Spec 05 over HTTP: the proposal PDF carries an AI draft grounded in the agency's memory with numbered sources,
 * the draft is exposed for review and stored encrypted, and approval keeps it (it used to drop the AI text).
 */
@SpringBootTest(properties = {"llm.provider=stub", "knowledge.vector-store=jdbc"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class GroundedDraftFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void proposalPdfIsAGroundedDraftWithSourcesAndApprovalKeepsIt() throws Exception {
        String token = agency();
        long clientId = id(call(post("/api/clients").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Taller\"}"), token).andExpect(status().isCreated()).andReturn());
        mockMvc.perform(multipart("/api/knowledge/documents")
                        .file(new MockMultipartFile("file", "P01-propuesta.pdf", MediaType.APPLICATION_PDF_VALUE,
                                Files.readAllBytes(Path.of("seed/pdf/train/P01-propuesta.pdf"))))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        MvcResult quoted = call(post("/api/quotations").contentType(MediaType.APPLICATION_JSON).content(
                "{\"source\":\"WEB_FORM\",\"clientId\":" + clientId + ",\"payload\":{\"description\":"
                        + "\"Necesito una tienda en linea con carrito para mi taller de motos.\"}}"), token)
                .andExpect(status().isCreated()).andReturn();
        Number proposalId = JsonPath.read(quoted.getResponse().getContentAsString(), "$.proposalId");
        String base = "/api/proposals/" + proposalId.longValue();

        call(get(base), token).andExpect(status().isOk())
                .andExpect(jsonPath("$.draft.origin").value("AI"))
                .andExpect(jsonPath("$.draft.sections[0].title").value("Alcance"))
                .andExpect(jsonPath("$.draft.sources[0].number").value(1))
                .andExpect(jsonPath("$.draft.sources[0].documentName").value("P01-propuesta.pdf"));

        String quotedPdf = pdfText(call(get(base + "/documents/PROPOSAL"), token).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        assertThat(quotedPdf).contains("Resumen ejecutivo").containsSubsequence("Alcance", "Supuestos",
                "Exclusiones", "Fuentes", "[1] P01-propuesta.pdf, p. 1");

        call(post(base + "/approve"), token).andExpect(status().isOk());
        String approvedPdf = pdfText(call(get(base + "/documents/PROPOSAL"), token).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        assertThat(approvedPdf).as("approval must keep the AI summary and the sources")
                .contains("Resumen ejecutivo").contains("Fuentes").contains("P01-propuesta.pdf");

        List<String> stored = jdbc.queryForList("select drafted_content from proposals where id = ?", String.class,
                proposalId.longValue());
        assertThat(stored).singleElement().asString().startsWith("v1:").doesNotContain("Alcance");
    }

    private String agency() throws Exception {
        String email = "draft-" + UUID.randomUUID().toString().substring(0, 8) + "@demo.co";
        MvcResult registered = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agencyName\":\"Agencia Draft\",\"ownerFullName\":\"Owner\",\"email\":\"" + email
                                + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated()).andReturn();
        String token = AuthCookieTests.cookie(registered, "cz_access");
        long catalogId = id(call(post("/api/catalog").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Web\"}"), token).andExpect(status().isCreated()).andReturn());
        call(post("/api/catalog/" + catalogId + "/requirement-types").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Tienda en linea\",\"description\":\"tienda, productos, carrito\","
                        + "\"estimatedHours\":60}"), token).andExpect(status().isCreated());
        long roleId = id(call(post("/api/roles").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Developer\"}"), token).andExpect(status().isCreated()).andReturn());
        call(post("/api/roles/" + roleId + "/rates").contentType(MediaType.APPLICATION_JSON)
                .content("{\"copPerHour\":95000}"), token).andExpect(status().isCreated());
        return token;
    }

    private String pdfText(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private long id(MvcResult result) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return value.longValue();
    }
}
