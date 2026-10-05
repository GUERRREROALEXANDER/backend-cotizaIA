package com.cotizaia.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Issue #22: Render probes /actuator/health without a token, so it must be public and answer UP, while every other
 * actuator endpoint (env, beans, ...) stays unexposed and nothing leaks configuration or secrets.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ActuatorHealthTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthIsPublicAndUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void sensitiveActuatorEndpointsAreNotReachable() throws Exception {
        mockMvc.perform(get("/actuator/env")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/actuator/beans")).andExpect(status().is4xxClientError());
    }

    @Test
    void openApiDocumentIsPublicForClientGeneration() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("CotizaIA API"));
    }
}
