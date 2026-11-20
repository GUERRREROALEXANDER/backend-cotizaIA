package com.cotizaia.agent.whatsapp;

import com.cotizaia.config.RateLimiter;
import com.cotizaia.domain.AppUser;
import com.cotizaia.repository.AppUserRepository;
import com.cotizaia.service.whatsapp.WhatsappContactService;
import java.time.Duration;
import java.util.Optional;

/**
 * Decides who answers a message received on the CotizaIA number. A number an agency user verified with a code
 * talks to {@link WhatsappQuoteChat} and can create quotations for that agency; anyone else talks to the sales
 * {@link WhatsappAssistant}, which never sees agency data. The phone comes from WhatsApp itself through the service,
 * so a sender cannot claim someone else's number by typing it.
 */
public class WhatsappInbox {

    /** Agency users can chat more than strangers, but still not without limit. */
    static final int STAFF_MESSAGES_PER_HOUR = 120;

    private final WhatsappContactService contacts;
    private final AppUserRepository users;
    private final WhatsappQuoteChat quoteChat;
    private final WhatsappAssistant assistant;
    private final RateLimiter limiter;

    public WhatsappInbox(WhatsappContactService contacts, AppUserRepository users, WhatsappQuoteChat quoteChat,
            WhatsappAssistant assistant, RateLimiter limiter) {
        this.contacts = contacts;
        this.users = users;
        this.quoteChat = quoteChat;
        this.assistant = assistant;
        this.limiter = limiter;
    }

    /** The reply to send, or empty to stay quiet. {@code phone} is the sender's number when WhatsApp exposes it. */
    public Optional<String> reply(String from, String phone, String text) {
        Optional<AppUser> staff = phone == null || phone.isBlank() ? Optional.empty()
                : contacts.findByPhone(phone.startsWith("+") ? phone : "+" + phone)
                        .flatMap(contact -> users.findById(contact.getUserId()));
        if (staff.isEmpty()) {
            return assistant.reply(from, text);
        }
        if (!limiter.tryAcquire("whatsapp-staff:" + staff.get().getId(), STAFF_MESSAGES_PER_HOUR,
                Duration.ofHours(1)).allowed()) {
            return Optional.empty();
        }
        return Optional.of(quoteChat.reply(staff.get(), text));
    }
}
