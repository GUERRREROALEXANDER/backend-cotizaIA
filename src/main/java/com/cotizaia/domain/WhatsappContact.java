package com.cotizaia.domain;

import com.cotizaia.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A WhatsApp number its owner proved they hold with a code sent to it. Only verified, enabled contacts ever
 * receive a notice, so a mistyped number cannot leak an agency's quotations to a stranger.
 */
@Entity
@Table(name = "whatsapp_contacts")
public class WhatsappContact {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String phone;

    /** Blind index of the number: how an incoming WhatsApp message finds its user without decrypting every row. */
    @Column(name = "phone_hash", length = 64)
    private String phoneHash;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt;

    protected WhatsappContact() {
    }

    /** A freshly verified number, with notices switched on: receiving them is why it was verified. */
    public WhatsappContact(Long userId, String phone, Instant verifiedAt) {
        if (userId == null || phone == null || phone.isBlank() || verifiedAt == null) {
            throw new IllegalArgumentException("user, phone and verification time are required");
        }
        this.userId = userId;
        this.phone = phone;
        this.enabled = true;
        this.verifiedAt = verifiedAt;
    }

    /** Replaces the number after a new verification; notices switch back on. */
    public void reverify(String newPhone, Instant at) {
        if (newPhone == null || newPhone.isBlank() || at == null) {
            throw new IllegalArgumentException("phone and verification time are required");
        }
        this.phone = newPhone;
        this.enabled = true;
        this.verifiedAt = at;
    }

    public void setPhoneHash(String phoneHash) {
        this.phoneHash = phoneHash;
    }

    public String getPhoneHash() {
        return phoneHash;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Long getUserId() {
        return userId;
    }

    public String getPhone() {
        return phone;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }
}
