package com.cotizaia.service.whatsapp;

import com.cotizaia.domain.WhatsappContact;
import com.cotizaia.repository.WhatsappContactRepository;
import java.time.Clock;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * Links a user's WhatsApp number in two steps: a six-digit code is queued to the typed number, and only typing it
 * back proves the user holds that number. A user has at most one number.
 */
public class WhatsappContactService {

    private final WhatsappContactRepository contacts;
    private final WhatsappOutbox outbox;
    private final PhoneVerifications verifications;
    private final Clock clock;

    public WhatsappContactService(WhatsappContactRepository contacts, WhatsappOutbox outbox,
            PhoneVerifications verifications, Clock clock) {
        this.contacts = contacts;
        this.outbox = outbox;
        this.verifications = verifications;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<WhatsappContact> find(Long userId) {
        return contacts.findById(userId);
    }

    /** Queues a code to the number. It arrives only while the WhatsApp service is running. */
    @Transactional
    public void startVerification(Long userId, String typedPhone) {
        String phone = PhoneNumbers.normalize(typedPhone);
        String code = verifications.start(userId, phone);
        outbox.queue(phone, codeText(code), PhoneVerifications.LIFETIME);
    }

    /** Links the number this code proves; empty when the code is wrong, expired or already used. */
    @Transactional
    public Optional<WhatsappContact> verify(Long userId, String code) {
        return verifications.confirm(userId, code).map(phone -> contacts.findById(userId)
                .map(existing -> {
                    existing.reverify(phone, clock.instant());
                    return existing;
                })
                .orElseGet(() -> contacts.save(new WhatsappContact(userId, phone, clock.instant()))));
    }

    @Transactional
    public WhatsappContact setEnabled(Long userId, boolean enabled) {
        WhatsappContact contact = require(userId);
        contact.setEnabled(enabled);
        return contact;
    }

    @Transactional
    public void remove(Long userId) {
        contacts.delete(require(userId));
    }

    private WhatsappContact require(Long userId) {
        return contacts.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("No WhatsApp number on this account"));
    }

    static String codeText(String code) {
        return "Tu código de CotizaIA es " + code + ". Vence en " + PhoneVerifications.LIFETIME.toMinutes()
                + " minutos. No lo compartas con nadie.";
    }
}
