package com.cotizaia.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class RateLimitFilterTests {

    private final RateLimitProperties properties = new RateLimitProperties();
    private final RateLimitFilter filter = new RateLimitFilter(properties,
            new RateLimiter(Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneOffset.UTC)));

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private static MockHttpServletRequest request(String method, String path, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(ip);
        return request;
    }

    private MockHttpServletResponse send(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static void signInAgency(String agencyId) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("1").claim("agencyId", agencyId).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @Test
    void theNextLoginAfterTheBudgetGetsA429WithRetryAfter() throws Exception {
        properties.setAuth(new RateLimitProperties.Bucket(2, Duration.ofMinutes(15)));
        send(request("POST", "/api/auth/login", "10.0.0.1"));
        send(request("POST", "/api/auth/login", "10.0.0.1"));

        MockHttpServletResponse refused = send(request("POST", "/api/auth/login", "10.0.0.1"));

        assertThat(refused.getStatus()).isEqualTo(429);
        assertThat(refused.getHeader("Retry-After")).isEqualTo("900");
        assertThat(refused.getContentAsString()).isEqualTo("{\"error\":\"Too many requests\"}");
    }

    @Test
    void loginBudgetsArePerClientIp() throws Exception {
        properties.setAuth(new RateLimitProperties.Bucket(1, Duration.ofMinutes(15)));
        send(request("POST", "/api/auth/login", "10.0.0.1"));

        assertThat(send(request("POST", "/api/auth/login", "10.0.0.2")).getStatus()).isEqualTo(200);
    }

    @Test
    void verificationEndpointsShareTheLoginBudget() throws Exception {
        properties.setAuth(new RateLimitProperties.Bucket(1, Duration.ofMinutes(15)));
        send(request("POST", "/api/auth/resend-verification", "10.0.0.1"));

        assertThat(send(request("POST", "/api/auth/verify-email", "10.0.0.1")).getStatus()).isEqualTo(429);
    }

    @Test
    void ordinaryRequestsAreNeverLimited() throws Exception {
        properties.setAuth(new RateLimitProperties.Bucket(0, Duration.ofMinutes(15)));

        assertThat(send(request("GET", "/api/proposals", "10.0.0.1")).getStatus()).isEqualTo(200);
        assertThat(send(request("GET", "/api/auth/login", "10.0.0.1")).getStatus()).isEqualTo(200);
    }

    @Test
    void aiCallsAreCountedPerAgency() throws Exception {
        properties.setAi(new RateLimitProperties.Bucket(1, Duration.ofMinutes(1)));
        signInAgency("3");
        send(request("POST", "/api/briefs/12/process", "10.0.0.1"));

        assertThat(send(request("POST", "/api/briefs/13/process", "10.0.0.9")).getStatus()).isEqualTo(429);

        signInAgency("4");
        assertThat(send(request("POST", "/api/briefs/13/process", "10.0.0.9")).getStatus()).isEqualTo(200);
    }

    @Test
    void anonymousAiCallsPassThroughForTheSecurityLayerToReject() throws Exception {
        properties.setAi(new RateLimitProperties.Bucket(0, Duration.ofMinutes(1)));

        assertThat(send(request("POST", "/api/quotations", "10.0.0.1")).getStatus()).isEqualTo(200);
    }

    @Test
    void turningLimitsOffLetsEverythingThrough() throws Exception {
        properties.setEnabled(false);
        properties.setAuth(new RateLimitProperties.Bucket(0, Duration.ofMinutes(15)));

        assertThat(send(request("POST", "/api/auth/login", "10.0.0.1")).getStatus()).isEqualTo(200);
    }
}
