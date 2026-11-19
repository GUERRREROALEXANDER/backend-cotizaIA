package com.cotizaia.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cotizaia.mail.MailProperties;
import com.cotizaia.repository.AppUserRepository;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Shared fixture for the spec 16 verification flow: a real in-process SMTP server (GreenMail) the
 * application talks to through the real {@code SmtpEmailGateway}, so "the mail is sent" means an
 * actual SMTP conversation, and the link the test follows is the link that arrived.
 *
 * <p>GreenMail binds an ephemeral port, so its address enters the context through
 * {@link DynamicPropertySource}; {@code mail.enabled=true} is the only difference from production
 * defaults. Each subclass decides the verification flag through its own {@code @SpringBootTest}
 * properties.</p>
 */
@AutoConfigureMockMvc
abstract class AbstractEmailVerificationTests {

    static final String PUBLIC_URL = "https://app.cotizaia.test";

    static final String SUBJECT = "Confirma tu correo en CotizaIA";

    private static final Pattern LINK = Pattern.compile(
            Pattern.quote(PUBLIC_URL) + "/verify-email\\?token=([A-Za-z0-9_-]+)");

    static final GreenMail GREEN_MAIL = new GreenMail(new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP));

    static {
        GREEN_MAIL.start();
    }

    @DynamicPropertySource
    static void mailServer(DynamicPropertyRegistry registry) {
        registry.add("mail.enabled", () -> "true");
        registry.add("mail.host", () -> "127.0.0.1");
        registry.add("mail.port", () -> GREEN_MAIL.getSmtp().getPort());
        registry.add("mail.starttls", () -> "false");
        registry.add("mail.public-url", () -> PUBLIC_URL);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AppUserRepository users;

    @Autowired
    MailProperties mailProperties;

    @BeforeEach
    void emptyMailbox() throws Exception {
        GREEN_MAIL.purgeEmailFromAllMailboxes();
    }

    /** Registers a fresh agency and returns the address used, unique per test. */
    String register(String prefix) throws Exception {
        String email = prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@demo.co";
        registerResult(email).andReturn();
        return email;
    }

    org.springframework.test.web.servlet.ResultActions registerResult(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agencyName\":\"Agencia Mail\",\"ownerFullName\":\"Owner\","
                                + "\"email\":\"" + email + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated());
    }

    org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    org.springframework.test.web.servlet.ResultActions verify(String token) throws Exception {
        return mockMvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"));
    }

    org.springframework.test.web.servlet.ResultActions resend(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/resend-verification").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    /** Messages delivered to one address, in arrival order. */
    List<MimeMessage> messagesTo(String address) {
        List<MimeMessage> found = new ArrayList<>();
        for (MimeMessage message : GREEN_MAIL.getReceivedMessages()) {
            try {
                if (Arrays.stream(message.getAllRecipients())
                        .anyMatch(recipient -> recipient.toString().equals(address))) {
                    found.add(message);
                }
            } catch (jakarta.mail.MessagingException exception) {
                throw new IllegalStateException(exception);
            }
        }
        return found;
    }

    /** The token carried by the one link of a message, exactly as a human would click it. */
    String tokenOf(MimeMessage message) {
        String body = GreenMailUtil.getBody(message);
        Matcher matcher = LINK.matcher(body);
        assertThat(matcher.find()).as("the mail carries an absolute verification link").isTrue();
        return matcher.group(1);
    }

    boolean verifiedInDatabase(String email) {
        return users.findByLoginEmail(email).orElseThrow().isEmailVerified();
    }

    long userId(String email) {
        return users.findByLoginEmail(email).orElseThrow().getId();
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static Instant lastExpiry(JdbcTemplate jdbc, long userId) {
        return jdbc.queryForObject(
                "SELECT expires_at FROM email_verification_tokens WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                (resultSet, row) -> resultSet.getTimestamp(1).toInstant(), userId);
    }

    MvcResult perform(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return actions.andReturn();
    }
}
