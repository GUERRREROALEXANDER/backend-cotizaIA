package com.cotizaia.service.whatsapp;

import com.cotizaia.crypto.BlindIndex;
import com.cotizaia.domain.WhatsappContact;
import com.cotizaia.repository.WhatsappContactRepository;
import java.time.Clock;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * Links a user's WhatsApp number in two steps: a six-digit code is queued to the typed number, and only typing it
 * back proves the user holds that number. A user has at most one number, and a number belongs to at most one user.
 */
public class WhatsappContactService {

    private final WhatsappContactRepository contacts;
    private final WhatsappOutbox outbox;
    private final PhoneVerifications verifications;
    private final BlindIndex blindIndex;
    private final Clock clock;

    public WhatsappContactService(WhatsappContactRepository contacts, WhatsappOutbox outbox,
            PhoneVerifications verifications, BlindIndex blindIndex, Clock clock) {
        this.contacts = contacts;
        this.outbox = outbox;
        this.verifications = verifications;
        this.blindIndex = blindIndex;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<WhatsappContact> find(Long userId) {
        return contacts.findById(userId);
    }

    /** The verified contact of this number (E.164, as WhatsApp reports it), if any user verified it. */
    @Transactional(readOnly = true)
    public Optional<WhatsappContact> findByPhone(String phone) {
        String normalized;
        try {
            normalized = PhoneNumbers.normalize(phone);
        } catch (IllegalArgumentException notAPhone) {
            return Optional.empty();
        }
        return contacts.findByPhoneHash(blindIndex.hash(normalized));
    }

    /** Queues a code to the number. It arrives only while the WhatsApp service is running. */
    @Transactional
    public void startVerification(Long userId, String typedPhone) {
        String phone = PhoneNumbers.normalize(typedPhone);
        String code = verifications.start(userId, phone);
        outbox.queue(phone, codeText(code), PhoneVerifications.LIFETIME);
    }

    /**
     * Links the number this code proves; empty when the code is wrong, expired or already used. Proving a number
     * that another account had linked moves it here: whoever reads its messages is its holder now.
     */
    @Transactional
    public Optional<WhatsappContact> verify(Long userId, String code) {
        return verifications.confirm(userId, code).map(phone -> {
            String hash = blindIndex.hash(phone);
            contacts.findByPhoneHash(hash).filter(other -> !other.getUserId().equals(userId)).ifPresent(other -> {
                contacts.delete(other);
                contacts.flush();
            });
            WhatsappContact contact = contacts.findById(userId).map(existing -> {
                existing.reverify(phone, clock.instant());
                return existing;
            }).orElseGet(() -> new WhatsappContact(userId, phone, clock.instant()));
            contact.setPhoneHash(hash);
            return contacts.saveAndFlush(contact);
        });
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
