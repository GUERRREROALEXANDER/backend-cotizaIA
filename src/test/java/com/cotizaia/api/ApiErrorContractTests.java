package com.cotizaia.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pins the public error contract: framework-level failures answer with the same {@code {"error": ...}}
 * body as domain failures, and the OpenAPI document never exposes the JWT-derived {@link CurrentUser}.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ApiErrorContractTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void malformedPathVariableIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/proposals/abc").with(TestTokens.forAgency(1L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid value for parameter 'id'"));
    }

    @Test
    void unknownEnumQueryValueIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/proposals").param("status", "NOPE").with(TestTokens.forAgency(1L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid value for parameter 'status'"));
    }

    @Test
    void missingRequiredParameterIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/executions").with(TestTokens.forAgency(1L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing required parameter 'briefId'"));
    }

    @Test
    void constraintViolationOnParameterIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/executions").param("briefId", "0").with(TestTokens.forAgency(1L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"));
    }

    @Test
    void unknownRouteIsNotFound() throws Exception {
        mockMvc.perform(get("/api/does-not-exist").with(TestTokens.forAgency(1L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Resource not found"));
    }

    @Test
    void unsupportedMethodIsMethodNotAllowed() throws Exception {
        mockMvc.perform(delete("/api/auth/me").with(TestTokens.forAgency(1L)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("Method not supported"));
    }

    @Test
    void unsupportedContentTypeIsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/clients").with(TestTokens.forAgency(1L))
                        .contentType(MediaType.TEXT_PLAIN).content("name"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value("Unsupported media type"));
    }

    @Test
    void otherFrameworkErrorsKeepTheirStatus() throws Exception {
        mockMvc.perform(get("/api/proposals/1/documents/PROPOSAL").with(TestTokens.forAgency(1L))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotAcceptable());
    }

    @Test
    void openApiDoesNotExposeAuthenticatedIdentityAsParameters() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode paths = mapper.readTree(body).path("paths");
        assertThat(paths.path("/api/auth/me").path("get").path("parameters").isMissingNode()).isTrue();
        List<String> leaked = new ArrayList<>();
        paths.fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(operation ->
                operation.getValue().path("parameters").forEach(parameter -> {
                    String name = parameter.path("name").asText();
                    if (List.of("user", "userId", "agencyId", "role", "email").contains(name)) {
                        leaked.add(operation.getKey().toUpperCase() + " " + path.getKey() + " ?" + name);
                    }
                })));
        assertThat(leaked).isEmpty();
    }
}
