package com.cotizaia.ingest.gmail;

import com.cotizaia.crypto.BlindIndex;
import com.cotizaia.facade.QuotationFacade;
import com.cotizaia.repository.ClientRepository;
import com.cotizaia.service.BriefService;
import com.cotizaia.service.ClientService;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;

/**
 * Wires the Gmail intake only when {@code gmail.enabled=true}; with it off
 * (the default, and every test that does not opt in) none of these beans
 * exist and POST /api/ingest/email falls under the normal authenticated chain.
 */
@Configuration
@ConditionalOnProperty(prefix = "gmail", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(GmailProperties.class)
public class GmailIngestConfiguration {

    @Bean
    public GmailClient gmailClient(RestClient.Builder builder, GmailProperties properties) {
        return new GmailRestClient(builder, properties.oauth(), Clock.systemUTC());
    }

    @Bean
    public GmailIngestService gmailIngestService(GmailClient gmail, GmailProperties properties,
            GmailIngestedMessageRepository ledger, GmailSyncStateRepository cursors, ClientRepository clients,
            ClientService clientService, BlindIndex blindIndex, BriefService briefService, QuotationFacade facade) {
        return new GmailIngestService(gmail, properties, ledger, cursors, clients, clientService, blindIndex,
                briefService, facade, Clock.systemUTC());
    }

    // Nested configurations are component-scanned on their own, so each repeats gmail.enabled.
    @Configuration
    @EnableScheduling
    @ConditionalOnExpression("${gmail.enabled:false} and ${gmail.scheduler-enabled:true}")
    static class Scheduling {

        @Bean
        public GmailIngestScheduler gmailIngestScheduler(GmailIngestService ingest, GmailProperties properties) {
            return new GmailIngestScheduler(ingest, properties.push().enabled());
        }
    }

    @Configuration
    @ConditionalOnProperty(name = {"gmail.enabled", "gmail.push.enabled"}, havingValue = "true")
    static class Push {

        @Bean
        public PushTokenVerifier pushTokenVerifier(GmailProperties properties) {
            return new GooglePushTokenVerifier(properties.push().audience(), properties.push().serviceAccountEmail());
        }

        /**
         * Its own chain, ordered before the app's: the push carries a Google
         * OIDC token in Authorization, which the app's resource server would
         * reject as a bad app JWT before permitAll is even consulted. No
         * cookies, no session, no CSRF; the controller verifies the token.
         */
        @Bean
        @Order(0)
        public SecurityFilterChain gmailPushSecurityChain(HttpSecurity http) throws Exception {
            return http.securityMatcher("/api/ingest/email")
                    .csrf(csrf -> csrf.disable())
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                    .build();
        }
    }
}
