package com.cotizaia.api;

import static com.cotizaia.api.TestTokens.forAgency;
import static com.cotizaia.ingest.PdfFixtures.pdf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cotizaia.domain.Agency;
import com.cotizaia.domain.BriefAttachment;
import com.cotizaia.domain.Client;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.BriefAttachmentRepository;
import com.cotizaia.repository.ClientRepository;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * HTTP acceptance for PDF brief upload: a client's RFP arrives as a file, its
 * text becomes a normal PDF-channel brief, the original file is kept, and
 * pages without text are reported back to the operator.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class BriefUploadControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AgencyRepository agencyRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private BriefAttachmentRepository attachmentRepository;

    private Long agencyId;

    private Long clientId;

    @BeforeEach
    void createClient() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia PDF"));
        agencyId = agency.getId();
        clientId = clientRepository.saveAndFlush(
                new Client(agency, "Tienda Andina", "compras@andina.co")).getId();
    }

    @Test
    void createsAPdfBriefFromTheUploadedFile() throws Exception {
        byte[] file = pdf("Necesito una tienda online con pagos", "Entrega en diciembre");

        mockMvc.perform(upload(file, "rfp-andina.pdf").param("clientId", clientId.toString())
                        .with(forAgency(agencyId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.brief.source").value("PDF"))
                .andExpect(jsonPath("$.brief.clientId").value(clientId))
                .andExpect(jsonPath("$.brief.rawText", containsString("Necesito una tienda online con pagos")))
                .andExpect(jsonPath("$.brief.rawText", containsString("Entrega en diciembre")))
                .andExpect(jsonPath("$.fileName").value("rfp-andina.pdf"))
                .andExpect(jsonPath("$.pages").value(2))
                .andExpect(jsonPath("$.truncated").value(false))
                .andExpect(jsonPath("$.unreadablePages", hasSize(0)));
    }

    @Test
    void reportsUnreadablePages() throws Exception {
        byte[] file = pdf("Alcance del proyecto", "", "Presupuesto");

        mockMvc.perform(upload(file, "escaneado.pdf").param("clientId", clientId.toString())
                        .with(forAgency(agencyId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pages").value(3))
                .andExpect(jsonPath("$.unreadablePages[0]").value(2));
    }

    @Test
    void prependsTheOperatorNote() throws Exception {
        mockMvc.perform(upload(pdf("Sitio para restaurante"), "rfp.pdf")
                        .param("clientId", clientId.toString())
                        .param("note", "Llamar antes del viernes")
                        .with(forAgency(agencyId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.brief.rawText", containsString("Llamar antes del viernes")))
                .andExpect(jsonPath("$.brief.rawText", containsString("Sitio para restaurante")));
    }

    @Test
    void keepsTheOriginalFileAndServesItBack() throws Exception {
        byte[] file = pdf("Landing para evento");

        MvcResult created = mockMvc.perform(upload(file, "evento.pdf")
                        .param("clientId", clientId.toString()).with(forAgency(agencyId)))
                .andExpect(status().isCreated()).andReturn();
        Long briefId = briefId(created);

        BriefAttachment attachment = attachmentRepository.findByBriefId(briefId).orElseThrow();
        assertThat(attachment.getFileName()).isEqualTo("evento.pdf");
        assertThat(attachment.getPageCount()).isEqualTo(1);
        assertThat(attachment.getSizeBytes()).isEqualTo(file.length);
        assertThat(attachment.getContent()).isEqualTo(file);

        mockMvc.perform(get("/api/briefs/{id}/attachment", briefId).with(forAgency(agencyId)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", containsString("evento.pdf")))
                .andExpect(content().bytes(file));
    }

    @Test
    void attachmentIsNotFoundForTypedBriefsAndOtherAgencies() throws Exception {
        MvcResult created = mockMvc.perform(upload(pdf("Brief privado"), "privado.pdf")
                        .param("clientId", clientId.toString()).with(forAgency(agencyId)))
                .andExpect(status().isCreated()).andReturn();
        Long otherAgencyId = agencyRepository.saveAndFlush(new Agency("Otra agencia")).getId();

        mockMvc.perform(get("/api/briefs/{id}/attachment", briefId(created)).with(forAgency(otherAgencyId)))
                .andExpect(status().isNotFound());

        MvcResult typed = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/briefs").with(forAgency(agencyId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"EMAIL\",\"clientId\":" + clientId + ",\"payload\":{\"body\":\"Hola\"}}"))
                .andExpect(status().isCreated()).andReturn();
        Number typedId = com.jayway.jsonpath.JsonPath.read(typed.getResponse().getContentAsString(), "$.id");
        mockMvc.perform(get("/api/briefs/{id}/attachment", typedId.longValue()).with(forAgency(agencyId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void briefDetailShowsThePdfChannel() throws Exception {
        MvcResult created = mockMvc.perform(upload(pdf("Detalle visible"), "detalle.pdf")
                        .param("clientId", clientId.toString()).with(forAgency(agencyId)))
                .andExpect(status().isCreated()).andReturn();

        mockMvc.perform(get("/api/briefs/{id}", briefId(created)).with(forAgency(agencyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("PDF"));
    }

    @Test
    void rejectsFilesThatAreNotPdf() throws Exception {
        byte[] text = "no soy un pdf".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(upload(text, "brief.pdf").param("clientId", clientId.toString())
                        .with(forAgency(agencyId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("PDF")));
    }

    @Test
    void rejectsFilesOverTheSizeLimitWithPayloadTooLarge() throws Exception {
        byte[] huge = new byte[com.cotizaia.ingest.PdfBriefReader.MAX_BYTES + 1];
        byte[] header = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(header, 0, huge, 0, header.length);

        mockMvc.perform(upload(huge, "enorme.pdf").param("clientId", clientId.toString())
                        .with(forAgency(agencyId)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error", containsString("10 MB")));
    }

    @Test
    void rejectsANoteOverTheLimit() throws Exception {
        mockMvc.perform(upload(pdf("Brief"), "brief.pdf")
                        .param("clientId", clientId.toString())
                        .param("note", "x".repeat(1_001))
                        .with(forAgency(agencyId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("1000")));
    }

    @Test
    void rejectsClientsOfAnotherAgency() throws Exception {
        Long otherAgencyId = agencyRepository.saveAndFlush(new Agency("Agencia ajena")).getId();

        mockMvc.perform(upload(pdf("Brief"), "brief.pdf").param("clientId", clientId.toString())
                        .with(forAgency(otherAgencyId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsUnauthenticatedUploads() throws Exception {
        mockMvc.perform(upload(pdf("Brief"), "brief.pdf").param("clientId", clientId.toString()))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder upload(
            byte[] bytes, String fileName) {
        return multipart("/api/briefs/upload")
                .file(new MockMultipartFile("file", fileName, MediaType.APPLICATION_PDF_VALUE, bytes));
    }

    private Long briefId(MvcResult result) throws Exception {
        Number id = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.brief.id");
        return id.longValue();
    }
}
