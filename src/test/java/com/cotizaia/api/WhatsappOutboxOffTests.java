package com.cotizaia.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Without WHATSAPP_SERVICE_KEY nobody can drain the outbox, whatever key they send. */
@SpringBootTest(properties = "llm.provider=stub")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class WhatsappOutboxOffTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void outboxIsClosedWhileNoServiceKeyIsConfigured() throws Exception {
        mockMvc.perform(post("/api/whatsapp/outbox/claim").header("X-API-Key", ""))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(post("/api/whatsapp/outbox/claim")).andExpect(status().isServiceUnavailable());
    }
}
