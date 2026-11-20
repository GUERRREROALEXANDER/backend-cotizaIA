package com.cotizaia.service.whatsapp;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * WhatsApp settings ({@code whatsapp.*}).
 * <ul>
 * <li>{@code serviceKey}: the shared secret the WhatsApp service sends in {@code X-API-Key}; while it is blank the
 * service endpoints answer 503, so nothing can drain the queue or use the bot.</li>
 * <li>{@code noticeLifetime}: how long a proposal notice may wait for the service before it is dropped.</li>
 * <li>{@code siteUrl}: the page the assistant sends people to for their quotation, and the base of the links to a
 * proposal it sends to agency users.</li>
 * <li>{@code botNumber}: the number the WhatsApp service is linked to, shown in the app so users know where to
 * write; optional, E.164 ({@code +573001234567}).</li>
 * </ul>
 */
@ConfigurationProperties("whatsapp")
public record WhatsappProperties(String serviceKey, Duration noticeLifetime, String siteUrl, String botNumber) {

    /** The service key must be long enough that guessing it is not an option. */
    public static final int MIN_KEY_LENGTH = 32;

    @ConstructorBinding
    public WhatsappProperties {
        serviceKey = serviceKey == null ? "" : serviceKey.trim();
        noticeLifetime = noticeLifetime == null ? Duration.ofHours(1) : noticeLifetime;
        siteUrl = siteUrl == null ? "" : siteUrl.trim().replaceAll("/+$", "");
        botNumber = botNumber == null || botNumber.isBlank() ? "" : PhoneNumbers.normalize(botNumber);
        if (!serviceKey.isEmpty() && serviceKey.length() < MIN_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "whatsapp.service-key (WHATSAPP_SERVICE_KEY) must have at least " + MIN_KEY_LENGTH + " characters");
        }
        if (noticeLifetime.isNegative() || noticeLifetime.isZero()) {
            throw new IllegalArgumentException("whatsapp.notice-lifetime must be positive");
        }
        if (!siteUrl.isEmpty() && !siteUrl.matches("https?://\\S+")) {
            throw new IllegalArgumentException("whatsapp.site-url (WHATSAPP_SITE_URL) must be an http(s) link");
        }
    }

    public boolean serviceEnabled() {
        return !serviceKey.isEmpty();
    }
}
