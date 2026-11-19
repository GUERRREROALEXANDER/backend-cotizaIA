package com.cotizaia.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spec 16: mail is OFF by default. The untouched application context must expose the no-op adapter
 * and must not create a JavaMailSender at all, so a deployment that sets no MAIL_* variable boots
 * and behaves exactly as before the feature.
 */
@SpringBootTest
@ActiveProfiles("test")
class MailGatewayDefaultTests {

    @Autowired
    private ApplicationContext context;

    @Test
    void mailOffByDefaultMeansTheNoopAdapterAndNoSmtpConfiguration() {
        assertThat(context.getBean(EmailGateway.class)).isInstanceOf(NoopEmailGateway.class);
        assertThat(context.getBeanNamesForType(JavaMailSender.class)).isEmpty();
        MailProperties properties = context.getBean(MailProperties.class);
        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.isVerificationRequired()).isFalse();
        assertThat(properties.isSendToClient()).isFalse();
    }
}
