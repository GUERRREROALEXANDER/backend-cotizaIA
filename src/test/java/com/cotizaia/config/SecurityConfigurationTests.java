package com.cotizaia.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies origin restrictions and signing-key requirements at the security boundary.
 * Preflight must work before authentication so permitted browser clients can send bearer tokens.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SecurityConfigurationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void allowsConfiguredOriginPreflightWithoutAuthentication() throws Exception {
        mockMvc.perform(options("/api/briefs/1").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void rejectsUnknownOrigin() throws Exception {
        mockMvc.perform(options("/api/briefs/1").header("Origin", "https://unknown.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void rejectsShortSecretAndGeneratesIndependentDevelopmentKeys() {
        SecurityConfiguration configuration = new SecurityConfiguration();
        assertThatIllegalArgumentException().isThrownBy(() -> configuration.jwtSecretKey(
                new SecurityProperties("short", Duration.ofHours(8), "cotizaia")))
                .withMessageContaining("at least 32 UTF-8 bytes");
        SecurityProperties defaults = new SecurityProperties("", null, null);
        byte[] first = configuration.jwtSecretKey(defaults).getEncoded();
        byte[] second = configuration.jwtSecretKey(defaults).getEncoded();
        assertThat(first).hasSize(32).isNotEqualTo(second);
        assertThat(defaults.ttl()).isEqualTo(Duration.ofHours(8));
        assertThat(defaults.issuer()).isEqualTo("cotizaia");
    }
}
