package com.cotizaia.agent.whatsapp;

import com.cotizaia.agent.llm.LlmClient;
import com.cotizaia.config.RateLimiter;
import com.cotizaia.service.whatsapp.WhatsappProperties;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the WhatsApp assistant on the agent side, so the core WhatsApp services stay free of AI code. */
@Configuration
public class WhatsappAssistantConfiguration {

    @Bean
    public WhatsappAssistant whatsappAssistant(LlmClient llm, RateLimiter limiter, WhatsappProperties properties) {
        return new WhatsappAssistant(llm, new ConversationMemory(Clock.systemUTC()), limiter, properties.siteUrl());
    }
}
