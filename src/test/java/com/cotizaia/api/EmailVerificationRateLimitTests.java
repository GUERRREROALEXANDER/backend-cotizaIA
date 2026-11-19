package com.cotizaia.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Spec 16: verify-email and resend-verification are public, so they reuse the credential rate limit
 * (per client IP, shared with login/register) instead of opening a fresh unthrottled surface.
 */
@SpringBootTest(properties = {"llm.provider=stub", "security.rate-limit.enabled=true",
        "security.rate-limit.auth.capacity=2", "security.rate-limit.auth.window=PT15M"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class EmailVerificationRateLimitTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void bothVerificationEndpointsShareTheCredentialBucket() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(verify("10.20.0.1")).andExpect(status().isBadRequest());
        }
        mockMvc.perform(verify("10.20.0.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error").value("Too many requests"));
        mockMvc.perform(resend("10.20.0.1")).andExpect(status().isTooManyRequests());

        // A different client keeps its own window.
        mockMvc.perform(resend("10.20.0.2")).andExpect(status().isNoContent());
    }

    private MockHttpServletRequestBuilder verify(String ip) {
        return fromIp(post("/api/auth/verify-email"), ip)
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"nope\"}");
    }

    private MockHttpServletRequestBuilder resend(String ip) {
        return fromIp(post("/api/auth/resend-verification"), ip)
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"nadie@demo.co\"}");
    }

    private MockHttpServletRequestBuilder fromIp(MockHttpServletRequestBuilder request, String ip) {
        return request.with(servletRequest -> {
            servletRequest.setRemoteAddr(ip);
            return servletRequest;
        });
    }
}
