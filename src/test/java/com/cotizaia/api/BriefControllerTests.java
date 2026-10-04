package com.cotizaia.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cotizaia.domain.Agency;
import com.cotizaia.domain.Client;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.ClientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * HTTP acceptance for issue #5: POSTing the same content in three different
 * channel payload shapes yields an identical normalized Brief, and the raw
 * payload is retrievable for audit.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class BriefControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AgencyRepository agencyRepository;

    @Autowired
    private ClientRepository clientRepository;

    private Long clientId;

    @BeforeEach
    void createClient() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia API Brief"));
        Client client = clientRepository.saveAndFlush(
                new Client(agency, "Restaurante El Sabor", "contacto@elsabor.co"));
        clientId = client.getId();
    }

    @Test
    void threePayloadShapesReturnAnIdenticalNormalizedBrief() throws Exception {
        String email = body("EMAIL", "{\"subject\":\"Cotizacion\",\"body\":\"Necesito una pagina web\"}");
        String whatsapp = body("WHATSAPP", "{\"from\":\"+57\",\"message\":\"Necesito una pagina web\"}");
        String webForm = body("WEB_FORM", "{\"description\":\"Necesito una pagina web\"}");

        String emailText = postAndReadRawText(email);
        String whatsappText = postAndReadRawText(whatsapp);
        String webFormText = postAndReadRawText(webForm);

        org.assertj.core.api.Assertions.assertThat(emailText)
                .isEqualTo("Necesito una pagina web")
                .isEqualTo(whatsappText)
                .isEqualTo(webFormText);
    }

    @Test
    void storesAndReturnsTheRawPayloadForAudit() throws Exception {
        String payload = "{\"subject\":\"Cotizacion\",\"from\":\"cliente@elsabor.co\","
                + "\"body\":\"Hola\"}";

        MvcResult created = mockMvc.perform(post("/api/briefs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("EMAIL", payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rawPayload", containsString("cliente@elsabor.co")))
                .andReturn();

        Long id = extractId(created);

        mockMvc.perform(get("/api/briefs/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("EMAIL"))
                .andExpect(jsonPath("$.rawText").value("Hola"))
                .andExpect(jsonPath("$.rawPayload", containsString("Cotizacion")));
    }

    @Test
    void rejectsUnknownClientWithNotFound() throws Exception {
        mockMvc.perform(post("/api/briefs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"EMAIL\",\"clientId\":-1,\"payload\":{\"body\":\"x\"}}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsPayloadMissingRequiredFieldWithBadRequest() throws Exception {
        mockMvc.perform(post("/api/briefs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("WHATSAPP", "{\"from\":\"+57\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("message")));
    }

    @Test
    void rejectsUnknownSourceWithBadRequest() throws Exception {
        mockMvc.perform(post("/api/briefs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"SMOKE_SIGNAL\",\"clientId\":" + clientId
                                + ",\"payload\":{\"body\":\"x\"}}"))
                .andExpect(status().isBadRequest());
    }

    private String body(String source, String payloadJson) {
        return "{\"source\":\"" + source + "\",\"clientId\":" + clientId
                + ",\"payload\":" + payloadJson + "}";
    }

    private String postAndReadRawText(String json) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/briefs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.rawText");
    }

    private Long extractId(MvcResult result) throws Exception {
        Number id = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }
}
