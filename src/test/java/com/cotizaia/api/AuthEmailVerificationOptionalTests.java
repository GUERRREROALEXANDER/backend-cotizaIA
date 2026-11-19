package com.cotizaia.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.icegreen.greenmail.util.GreenMailUtil;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spec 16 with mail on but verification not enforced: the account is usable right away
 * ({@code emailVerificationRequired=false} in the register response) while the verification mail is
 * still delivered, so the owner can confirm the address whenever they want.
 */
@SpringBootTest(properties = {"llm.provider=stub", "mail.verification-required=false"})
@ActiveProfiles("test")
class AuthEmailVerificationOptionalTests extends AbstractEmailVerificationTests {

    @Test
    void registerKeepsTheAccountVerifiedAndStillSendsTheLink() throws Exception {
        String email = "opcional-" + System.nanoTime() + "@demo.co";

        registerResult(email).andExpect(jsonPath("$.emailVerificationRequired").value(false));
        assertThat(verifiedInDatabase(email)).as("no verification is enforced").isTrue();
        login(email, "secret123").andExpect(status().isOk());

        List<MimeMessage> mails = messagesTo(email);
        assertThat(mails).hasSize(1);
        assertThat(mails.get(0).getSubject()).isEqualTo(SUBJECT);
        assertThat(GreenMailUtil.getBody(mails.get(0))).contains(PUBLIC_URL + "/verify-email?token=");

        // Redeeming the link is harmless for an already verified account.
        verify(tokenOf(mails.get(0))).andExpect(status().isNoContent());
        assertThat(verifiedInDatabase(email)).isTrue();
    }

    @Test
    void resendDoesNotMailAVerifiedAccount() throws Exception {
        String email = register("opcional-reenvio");
        GREEN_MAIL.purgeEmailFromAllMailboxes();

        resend(email).andExpect(status().isNoContent()).andExpect(content().string(""));

        assertThat(GREEN_MAIL.getReceivedMessages()).isEmpty();
    }
}
