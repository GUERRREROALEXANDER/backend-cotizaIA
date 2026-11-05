package com.cotizaia.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cotizaia.api.AuthCookieTests;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Spec 08: brute force and LLM cost abuse stop at a 429 before reaching any controller. */
@SpringBootTest(properties = {"llm.provider=stub", "security.rate-limit.enabled=true",
        "security.rate-limit.auth.capacity=3",
        "security.rate-limit.auth.window=PT15M", "security.rate-limit.ai.capacity=2",
        "security.rate-limit.ai.window=PT1M"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class RateLimitTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginAttemptsAreLimitedPerClientIp() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(login("10.0.0.7")).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(login("10.0.0.7"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error").value("Too many requests"));
        mockMvc.perform(login("10.0.0.8")).andExpect(status().isUnauthorized());
    }

    @Test
    void aiEndpointsAreLimitedPerAgencyNotGlobally() throws Exception {
        String first = token("10.1.0.1");
        String second = token("10.1.0.2");
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(search(first)).andExpect(status().isOk());
        }
        mockMvc.perform(search(first)).andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        mockMvc.perform(search(second)).andExpect(status().isOk());
        mockMvc.perform(get("/api/clients").header("Authorization", "Bearer " + first)).andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder login(String ip) {
        return post("/api/auth/login").with(request -> {
            request.setRemoteAddr(ip);
            return request;
        }).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"nobody@demo.co\",\"password\":\"wrong-pass\"}");
    }

    private MockHttpServletRequestBuilder search(String token) {
        return get("/api/knowledge/search").param("q", "tienda").header("Authorization", "Bearer " + token);
    }

    private String token(String ip) throws Exception {
        String email = "rl-" + UUID.randomUUID().toString().substring(0, 8) + "@demo.co";
        MvcResult registered = mockMvc.perform(post("/api/auth/register").with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                }).contentType(MediaType.APPLICATION_JSON).content("{\"agencyName\":\"Agencia RL\","
                        + "\"ownerFullName\":\"Owner\",\"email\":\"" + email + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated()).andReturn();
        return AuthCookieTests.cookie(registered, "cz_access");
    }
}
