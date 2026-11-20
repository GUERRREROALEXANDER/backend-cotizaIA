package com.cotizaia.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The whole WhatsApp loop over HTTP, as the Node service and the browser drive it: a user asks for a code, the
 * service claims it from the outbox and reports it sent, and the user types it back to link the number.
 */
@SpringBootTest(properties = {"llm.provider=stub", "whatsapp.service-key=" + WhatsappApiTests.KEY})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class WhatsappApiTests {

    static final String KEY = "test-service-key-0123456789abcdef0123";

    private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void codeTravelsThroughTheOutboxAndLinksTheNumber() throws Exception {
        String token = register();
        String phone = randomPhone();

        mockMvc.perform(post("/api/auth/me/whatsapp/verification").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"" + spaced(phone) + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.phone").value("*********" + phone.substring(phone.length() - 4)))
                .andExpect(jsonPath("$.expiresInSeconds").value(600));

        Map<String, Object> message = claimFor(phone);
        String body = (String) message.get("body");
        Matcher matcher = CODE.matcher(body);
        assertThat(matcher.find()).as("the message carries the code: %s", body).isTrue();
        long id = ((Number) message.get("id")).longValue();
        mockMvc.perform(post("/api/whatsapp/outbox/" + id + "/sent").header("X-API-Key", KEY))
                .andExpect(status().isNoContent());
        // Reporting twice (a retry after a lost response) is harmless.
        mockMvc.perform(post("/api/whatsapp/outbox/" + id + "/sent").header("X-API-Key", KEY))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/me/whatsapp/verify").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + matcher.group(1) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.phone").value("*********" + phone.substring(phone.length() - 4)));

        mockMvc.perform(get("/api/auth/me/whatsapp").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verifiedAt").isNotEmpty());
        mockMvc.perform(patch("/api/auth/me/whatsapp").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        mockMvc.perform(delete("/api/auth/me/whatsapp").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/auth/me/whatsapp").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void wrongCodesAndBadNumbersAreRefused() throws Exception {
        String token = register();

        mockMvc.perform(post("/api/auth/me/whatsapp/verification").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"3001234567\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/me/whatsapp/verify").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/me/whatsapp/verify").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"12ab\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/auth/me/whatsapp").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/auth/me/whatsapp")).andExpect(status().isUnauthorized());
    }

    @Test
    void aSecondCodeWithinAMinuteIsThrottled() throws Exception {
        String token = register();
        String phone = randomPhone();
        String request = "{\"phone\":\"" + phone + "\"}";

        mockMvc.perform(post("/api/auth/me/whatsapp/verification").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/auth/me/whatsapp/verification").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void onlyTheServiceKeyOpensTheOutbox() throws Exception {
        mockMvc.perform(post("/api/whatsapp/outbox/claim")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/whatsapp/outbox/claim").header("X-API-Key", KEY + "x"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/whatsapp/outbox/claim").param("limit", "51").header("X-API-Key", KEY))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/whatsapp/outbox/999999/sent").header("X-API-Key", KEY))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/whatsapp/outbox/1/failed").header("X-API-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"error\":\"see https://evil.example\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aNumberWithoutWhatsappIsReportedAndNotRetried() throws Exception {
        String token = register();
        String phone = randomPhone();
        mockMvc.perform(post("/api/auth/me/whatsapp/verification").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"" + phone + "\"}"))
                .andExpect(status().isAccepted());
        long id = ((Number) claimFor(phone).get("id")).longValue();

        mockMvc.perform(post("/api/whatsapp/outbox/" + id + "/failed").header("X-API-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"error\":\"number is not on WhatsApp\"}"))
                .andExpect(status().isNoContent());

        assertThat(claimAll()).noneMatch(message -> phone.equals(message.get("phone")));
    }

    @Test
    void theAssistantEndpointNeedsTheServiceKeyAndAnswers() throws Exception {
        String message = "{\"from\":\"573001234567@s.whatsapp.net\",\"text\":\"quiero una pagina web\"}";

        mockMvc.perform(post("/api/whatsapp/bot/reply").contentType(MediaType.APPLICATION_JSON).content(message))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/whatsapp/bot/reply").header("X-API-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"from\":\"x\",\"text\":\"\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/whatsapp/bot/reply").header("X-API-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(message))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("CotizaIA")));
    }

    private Map<String, Object> claimFor(String phone) throws Exception {
        return claimAll().stream().filter(message -> phone.equals(message.get("phone"))).findFirst()
                .orElseThrow(() -> new AssertionError("no message queued for " + phone));
    }

    private List<Map<String, Object>> claimAll() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/whatsapp/outbox/claim").param("limit", "50")
                        .header("X-API-Key", KEY))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.items");
    }

    private String register() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MvcResult registered = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agencyName\":\"WhatsApp " + suffix + "\",\"ownerFullName\":\"Owner\","
                                + "\"email\":\"whatsapp-" + suffix + "@demo.co\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return AuthCookieTests.cookie(registered, "cz_access");
    }

    private static String randomPhone() {
        return "+57300" + ThreadLocalRandom.current().nextInt(1_000_000, 10_000_000);
    }

    /** The number as a person types it, with spaces, which must be ignored. */
    private static String spaced(String phone) {
        return phone.substring(0, 3) + " " + phone.substring(3, 6) + " " + phone.substring(6);
    }
}
