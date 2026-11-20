package com.cotizaia.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The whole "quote by WhatsApp" journey over HTTP, as the app and the WhatsApp service drive it: an agency user
 * verifies their number with a code, then chats with the CotizaIA number and gets a real proposal from the same
 * pipeline as the web app, including the agent's clarifying questions.
 */
@SpringBootTest(properties = {"llm.provider=stub", "whatsapp.service-key=" + WhatsappQuoteChatApiTests.KEY,
        "whatsapp.site-url=https://app.cotizaia.test", "whatsapp.bot-number=+57 301 094 4197"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class WhatsappQuoteChatApiTests {

    static final String KEY = "quote-chat-test-key-0123456789abcdef0123";

    private static final String RESTAURANT_BRIEF = "Tengo un restaurante y necesito una pagina web donde los clientes "
            + "vean el menu y puedan hacer reservas en linea";
    private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");
    private static final Pattern PROPOSAL = Pattern.compile("Cotización #(\\d+)");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aVerifiedUserQuotesByWhatsappAndGetsARealProposal() throws Exception {
        Agency agency = setUpAgency();
        String phone = verifiedPhone(agency.token());

        assertThat(say(phone, "hola")).contains("Hola Owner", "vinculado a su cuenta", "*cotizar*");
        assertThat(say(phone, "¡Cotizar!")).contains("¿Cómo se llama el cliente?");
        assertThat(say(phone, "Restaurante El Sabor")).contains("correo de Restaurante El Sabor");
        assertThat(say(phone, "no")).contains("Registré a Restaurante El Sabor", "cuénteme el proyecto");
        assertThat(say(phone, "web")).contains("Cuénteme un poco más");

        String done = say(phone, RESTAURANT_BRIEF);

        assertThat(done).contains("✅ Cotización #", "Restaurante El Sabor", "Total: $", "lista para su revisión",
                "https://app.cotizaia.test/#/propuestas");
        long proposalId = Long.parseLong(match(PROPOSAL, done));
        call(get("/api/proposals/" + proposalId), agency.token(), null, status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"));
        assertThat(say(phone, "gracias")).contains("*cotizar*");
    }

    @Test
    void theAgentsQuestionsAreAskedAndAnsweredInTheChat() throws Exception {
        Agency agency = setUpAgency();
        String phone = verifiedPhone(agency.token());
        String email = "cliente-" + UUID.randomUUID().toString().substring(0, 8) + "@demo.co";
        say(phone, "cotizar");
        say(phone, "Cliente Nuevo");
        assertThat(say(phone, "no es un correo")).contains("no parece válido");
        say(phone, email);

        String reply = say(phone, "hola, necesito ayuda con algo");

        assertThat(reply).contains("necesito un dato más");
        for (int round = 0; round < 4 && !reply.contains("✅"); round++) {
            reply = say(phone, "Una pagina web con menu digital y sistema de reservas");
        }
        assertThat(reply).contains("✅ Cotización #", "Cliente Nuevo");

        say(phone, "cotizar");
        say(phone, "Otro nombre");
        assertThat(say(phone, email.toUpperCase())).contains("Encontré a Cliente Nuevo");
    }

    @Test
    void theRobotStickerMatchesEachMoment() throws Exception {
        Agency agency = setUpAgency();
        String phone = verifiedPhone(agency.token());

        assertThat(sticker(phone, "hola")).isEqualTo("saludo");
        assertThat(sticker(phone, "cotizar")).isEqualTo("cotizar");
        assertThat(sticker(phone, "Cliente Sticker")).isNull();
        assertThat(sticker(phone, "no")).isNull();
        assertThat(sticker(phone, "hola, necesito ayuda con algo")).isEqualTo("pensando");
        String last = null;
        for (int round = 0; round < 4 && !"listo".equals(last); round++) {
            last = sticker(phone, "Una pagina web con menu digital y sistema de reservas");
        }
        assertThat(last).isEqualTo("listo");
        assertThat(sticker(randomPhone(), "Buenas tardes")).isEqualTo("saludo");
    }
    @Test
    void cancellingGoesBackToTheStart() throws Exception {
        Agency agency = setUpAgency();
        String phone = verifiedPhone(agency.token());
        say(phone, "cotizar");
        say(phone, "Cliente");

        assertThat(say(phone, "Cancelar")).contains("cancelé la cotización");
        assertThat(say(phone, "Cliente")).contains("*cotizar*").doesNotContain("correo");
    }

    @Test
    void anUnverifiedNumberTalksToTheSalesAssistant() throws Exception {
        String reply = say(randomPhone(), "quiero cotizar una pagina web");

        assertThat(reply).contains("CotizaIA").doesNotContain("¿Cómo se llama el cliente?");
    }

    @Test
    void anotherAccountCannotFloodANumberWithCodes() throws Exception {
        Agency first = setUpAgency();
        String phone = verifiedPhone(first.token());
        Agency second = setUpAgency();

        call(post("/api/auth/me/whatsapp/verification"), second.token(), "{\"phone\":\"" + phone + "\"}",
                status().isTooManyRequests());
        assertThat(say(phone, "hola")).contains("vinculado a su cuenta de Agencia " + first.suffix());
    }

    @Test
    void theAppKnowsTheBotNumber() throws Exception {
        Agency agency = setUpAgency();

        call(get("/api/auth/me/whatsapp/info"), agency.token(), null, status().isOk())
                .andExpect(jsonPath("$.botNumber").value("+573010944197"))
                .andExpect(jsonPath("$.serviceConfigured").value(true));
    }

    @Test
    void aMalformedPhoneIsRefused() throws Exception {
        mockMvc.perform(post("/api/whatsapp/bot/reply").header("X-API-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"x@lid\",\"phone\":\"abc\",\"text\":\"hola\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---- helpers ----

    private String say(String phone, String text) throws Exception {
        String body = "{\"from\":\"" + phone.substring(1) + "@s.whatsapp.net\",\"phone\":\"" + phone.substring(1)
                + "\",\"text\":" + quote(text) + "}";
        MvcResult result = mockMvc.perform(post("/api/whatsapp/bot/reply").header("X-API-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        String reply = JsonPath.read(result.getResponse().getContentAsString(), "$.reply");
        assertThat(reply).as("reply to %s", text).isNotBlank();
        return reply;
    }

    private String sticker(String phone, String text) throws Exception {
        String body = "{\"from\":\"" + phone.substring(1) + "@s.whatsapp.net\",\"phone\":\"" + phone.substring(1)
                + "\",\"text\":" + quote(text) + "}";
        MvcResult result = mockMvc.perform(post("/api/whatsapp/bot/reply").header("X-API-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.sticker");
    }
    private String verifiedPhone(String token) throws Exception {
        String phone = randomPhone();
        verify(token, phone);
        return phone;
    }

    private void verify(String token, String phone) throws Exception {
        call(post("/api/auth/me/whatsapp/verification"), token, "{\"phone\":\"" + phone + "\"}", status().isAccepted());
        MvcResult claimed = mockMvc.perform(post("/api/whatsapp/outbox/claim").param("limit", "50")
                        .header("X-API-Key", KEY))
                .andExpect(status().isOk())
                .andReturn();
        List<Map<String, Object>> items = JsonPath.read(claimed.getResponse().getContentAsString(), "$.items");
        String body = items.stream().filter(item -> phone.equals(item.get("phone"))).map(item -> (String) item.get("body"))
                .findFirst().orElseThrow(() -> new AssertionError("no code queued for " + phone));
        call(post("/api/auth/me/whatsapp/verify"), token, "{\"code\":\"" + match(CODE, body) + "\"}", status().isOk());
    }

    private record Agency(String token, String suffix) {
    }

    private Agency setUpAgency() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MvcResult registered = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agencyName\":\"Agencia " + suffix + "\",\"ownerFullName\":\"Owner Prueba\","
                                + "\"email\":\"wa-owner-" + suffix + "@demo.co\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String token = AuthCookieTests.cookie(registered, "cz_access");
        Number catalogId = JsonPath.read(call(post("/api/catalog"), token, "{\"name\":\"Desarrollo web\"}",
                status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        for (String[] type : new String[][] {{"Diseno responsive", "pagina, web, sitio", "24"},
                {"Menu digital", "menu, carta", "12"}, {"Sistema de reservas", "reserva, reservas, agenda", "32"}}) {
            call(post("/api/catalog/" + catalogId + "/requirement-types"), token, "{\"name\":\"" + type[0]
                    + "\",\"description\":\"" + type[1] + "\",\"estimatedHours\":" + type[2] + "}", status().isCreated());
        }
        Number roleId = JsonPath.read(call(post("/api/roles"), token, "{\"name\":\"Developer\"}", status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        call(post("/api/roles/" + roleId + "/rates"), token, "{\"copPerHour\":120000}", status().isCreated());
        return new Agency(token, suffix);
    }

    private org.springframework.test.web.servlet.ResultActions call(MockHttpServletRequestBuilder request,
            String token, String json, ResultMatcher expected) throws Exception {
        request.header("Authorization", "Bearer " + token);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request).andExpect(expected);
    }

    private static String match(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        assertThat(matcher.find()).as("%s in: %s", pattern, text).isTrue();
        return matcher.group(1);
    }

    private static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String randomPhone() {
        return "+57301" + ThreadLocalRandom.current().nextInt(1_000_000, 10_000_000);
    }
}
