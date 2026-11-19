package com.cotizaia.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.icegreen.greenmail.util.GreenMailUtil;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spec 16 with verification enforced: the register call mails a working one-time link and leaves the
 * account unverified, login answers 403 until the link is redeemed, a link dies on first use and
 * after 24 hours, and resend answers the same 204 for every address.
 */
@SpringBootTest(properties = {"llm.provider=stub", "mail.verification-required=true"})
@ActiveProfiles("test")
class AuthEmailVerificationRequiredTests extends AbstractEmailVerificationTests {

    @Test
    void registerMailsAWorkingOneTimeLinkAndStartsUnverified() throws Exception {
        String email = register("verifica");

        assertThat(verifiedInDatabase(email)).as("the account starts unverified").isFalse();

        List<MimeMessage> mails = messagesTo(email);
        assertThat(mails).hasSize(1);
        MimeMessage mail = mails.get(0);
        assertThat(mail.getSubject()).isEqualTo(SUBJECT);
        String body = GreenMailUtil.getBody(mail);
        assertThat(body).contains(PUBLIC_URL + "/verify-email?token=");
        assertThat(body).contains("24 horas");

        String token = tokenOf(mail);
        assertThat(token).as("32 random bytes in URL-safe Base64 without padding").hasSize(43)
                .matches("[A-Za-z0-9_-]+");

        // Only the hash is stored: the raw token appears in no column of the table.
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT token_hash, expires_at, used_at FROM email_verification_tokens WHERE user_id = ?",
                userId(email));
        assertThat(rows).hasSize(1);
        assertThat(rows.toString()).doesNotContain(token);
        assertThat(String.valueOf(rows.get(0).get("token_hash")))
                .isEqualTo(sha256(token)).hasSize(64);
        assertThat(rows.get(0).get("used_at")).isNull();
        Instant expiry = lastExpiry(jdbc, userId(email));
        assertThat(Duration.between(Instant.now(), expiry))
                .isBetween(Duration.ofHours(23), Duration.ofHours(25));

        verify(token).andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(verifiedInDatabase(email)).isTrue();

        // Single use: the same link cannot be redeemed twice.
        verify(token).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid or expired token"));

        login(email, "secret123").andExpect(status().isOk());
    }

    @Test
    void loginIsForbiddenForAnUnverifiedAccountOnly() throws Exception {
        String email = register("login-403");

        login(email, "secret123")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Email not verified"));

        // The password is still checked first: a wrong password is 401, not "not verified".
        login(email, "wrong-password").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid email or password"));

        verify(tokenOf(messagesTo(email).get(0))).andExpect(status().isNoContent());
        login(email, "secret123").andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerificationRequired").value(false));
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String email = register("expira");
        String token = tokenOf(messagesTo(email).get(0));
        jdbc.update("UPDATE email_verification_tokens SET expires_at = ? WHERE user_id = ?",
                Timestamp.from(Instant.now().minusSeconds(60)), userId(email));

        verify(token).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid or expired token"));
        assertThat(verifiedInDatabase(email)).isFalse();
    }

    @Test
    void unknownTokenIsRejected() throws Exception {
        verify("not-a-real-token").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid or expired token"));
    }

    @Test
    void resendNeverRevealsWhetherTheAddressExists() throws Exception {
        String email = register("reenvio");
        String firstToken = tokenOf(messagesTo(email).get(0));
        GREEN_MAIL.purgeEmailFromAllMailboxes();

        // Unknown address: same 204 and no mail.
        resend("nadie-" + System.nanoTime() + "@demo.co").andExpect(status().isNoContent())
                .andExpect(content().string(""));
        assertThat(GREEN_MAIL.getReceivedMessages()).isEmpty();

        // Unverified account: same 204, and a new link arrives with a different token.
        resend(email).andExpect(status().isNoContent()).andExpect(content().string(""));
        List<MimeMessage> resent = messagesTo(email);
        assertThat(resent).hasSize(1);
        String second = tokenOf(resent.get(0));
        assertThat(second).isNotEqualTo(firstToken);

        // Verified account: same 204 and nothing is mailed.
        verify(second).andExpect(status().isNoContent());
        GREEN_MAIL.purgeEmailFromAllMailboxes();
        resend(email).andExpect(status().isNoContent());
        assertThat(GREEN_MAIL.getReceivedMessages()).isEmpty();
    }

    @Test
    void verificationEndpointsArePublicAndCsrfExempt() throws Exception {
        String email = "csrf-" + System.nanoTime() + "@demo.co";
        String access = AuthCookieTests.cookie(registerResult(email).andReturn(), "cz_access");
        GREEN_MAIL.purgeEmailFromAllMailboxes();

        // A browser-like caller: the session cookie rides along and there is no X-XSRF-TOKEN. Login is
        // exempt the same way, so these two must reach the controller instead of failing CSRF with 403.
        mockMvc.perform(post("/api/auth/resend-verification")
                        .cookie(new Cookie("cz_access", access))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isNoContent());
        assertThat(messagesTo(email)).hasSize(1);

        mockMvc.perform(post("/api/auth/verify-email")
                        .cookie(new Cookie("cz_access", access))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"nope\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid or expired token"));
    }
}
