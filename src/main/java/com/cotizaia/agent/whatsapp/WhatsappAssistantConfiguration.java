package com.cotizaia.agent.whatsapp;

import com.cotizaia.agent.llm.LlmClient;
import com.cotizaia.config.RateLimiter;
import com.cotizaia.crypto.BlindIndex;
import com.cotizaia.facade.QuotationFacade;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.AppUserRepository;
import com.cotizaia.repository.ClientRepository;
import com.cotizaia.repository.WhatsappQuoteSessionRepository;
import com.cotizaia.service.ClientService;
import com.cotizaia.service.whatsapp.WhatsappContactService;
import com.cotizaia.service.whatsapp.WhatsappProperties;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the WhatsApp chat on the agent side, so the core WhatsApp services stay free of AI code. */
@Configuration
public class WhatsappAssistantConfiguration {

    @Bean
    public WhatsappAssistant whatsappAssistant(LlmClient llm, RateLimiter limiter, WhatsappProperties properties) {
        return new WhatsappAssistant(llm, new ConversationMemory(Clock.systemUTC()), limiter, properties.siteUrl());
    }

    @Bean
    public WhatsappQuoteChat whatsappQuoteChat(QuotationFacade quotations, ClientService clientService,
            ClientRepository clients, AgencyRepository agencies, WhatsappQuoteSessionRepository sessions,
            BlindIndex blindIndex, WhatsappProperties properties) {
        return new WhatsappQuoteChat(quotations, clientService, clients, agencies, sessions, blindIndex,
                properties.siteUrl(), Clock.systemUTC());
    }

    @Bean
    public WhatsappInbox whatsappInbox(WhatsappContactService contacts, AppUserRepository users,
            WhatsappQuoteChat quoteChat, WhatsappAssistant assistant, RateLimiter limiter) {
        return new WhatsappInbox(contacts, users, quoteChat, assistant, limiter);
    }
}
