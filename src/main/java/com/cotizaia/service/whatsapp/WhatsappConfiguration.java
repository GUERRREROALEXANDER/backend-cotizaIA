package com.cotizaia.service.whatsapp;

import com.cotizaia.repository.WhatsappContactRepository;
import com.cotizaia.repository.WhatsappMessageRepository;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the WhatsApp pieces with the system clock; tests build them with a fixed one. */
@Configuration
@EnableConfigurationProperties(WhatsappProperties.class)
public class WhatsappConfiguration {

    @Bean
    public PhoneVerifications phoneVerifications() {
        return new PhoneVerifications(Clock.systemUTC());
    }

    @Bean
    public WhatsappOutbox whatsappOutbox(WhatsappMessageRepository messages) {
        return new WhatsappOutbox(messages, Clock.systemUTC());
    }

    @Bean
    public WhatsappContactService whatsappContactService(WhatsappContactRepository contacts, WhatsappOutbox outbox,
            PhoneVerifications verifications) {
        return new WhatsappContactService(contacts, outbox, verifications, Clock.systemUTC());
    }

    @Bean
    public WhatsappServiceKey whatsappServiceKey(WhatsappProperties properties) {
        return new WhatsappServiceKey(properties);
    }
}
