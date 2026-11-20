package com.cotizaia.api;

import com.cotizaia.domain.WhatsappContact;
import com.cotizaia.service.whatsapp.PhoneNumbers;
import com.cotizaia.service.whatsapp.PhoneVerifications;
import com.cotizaia.service.whatsapp.WhatsappContactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Links the signed-in user's WhatsApp number: request a code, type it back, then toggle or remove the notices.
 * The code travels through the WhatsApp outbox, so it arrives only while the WhatsApp service is running.
 */
@RestController
@RequestMapping("/api/auth/me/whatsapp")
@Tag(name = "WhatsApp")
public class WhatsappController {

    private final WhatsappContactService contacts;

    public WhatsappController(WhatsappContactService contacts) {
        this.contacts = contacts;
    }

    public record StartRequest(
            @Schema(example = "+573001234567", description = "With the country code; spaces and hyphens are ignored")
            @NotBlank @Size(max = 32) String phone) {
    }

    public record StartResponse(String phone, long expiresInSeconds) {
    }

    public record VerifyRequest(@NotNull @Pattern(regexp = "\\d{6}", message = "must be 6 digits") String code) {
    }

    public record PreferencesRequest(@NotNull Boolean enabled) {
    }

    /** The number is masked: the page only needs to recognize it, and a full number is personal data. */
    public record ContactResponse(String phone, boolean enabled, Instant verifiedAt) {

        static ContactResponse from(WhatsappContact contact) {
            return new ContactResponse(PhoneNumbers.masked(contact.getPhone()), contact.isEnabled(),
                    contact.getVerifiedAt());
        }
    }

    @GetMapping
    @Operation(summary = "The verified WhatsApp number of this account (404 when none)")
    public ContactResponse get(CurrentUser user) {
        return contacts.find(user.userId()).map(ContactResponse::from)
                .orElseThrow(() -> new NoSuchElementException("No WhatsApp number on this account"));
    }

    @PostMapping("/verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Send a 6-digit code to this number by WhatsApp (202: queued for the WhatsApp service)")
    public StartResponse start(@Valid @RequestBody StartRequest request, CurrentUser user) {
        contacts.startVerification(user.userId(), request.phone());
        return new StartResponse(PhoneNumbers.masked(PhoneNumbers.normalize(request.phone())),
                PhoneVerifications.LIFETIME.toSeconds());
    }

    @PostMapping("/verify")
    @Operation(summary = "Type back the code to link the number; notices start switched on")
    public ContactResponse verify(@Valid @RequestBody VerifyRequest request, CurrentUser user) {
        return contacts.verify(user.userId(), request.code()).map(ContactResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("This code is wrong or has expired"));
    }

    @PatchMapping
    @Operation(summary = "Switch WhatsApp notices on or off")
    public ContactResponse preferences(@Valid @RequestBody PreferencesRequest request, CurrentUser user) {
        return ContactResponse.from(contacts.setEnabled(user.userId(), request.enabled()));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove the WhatsApp number from this account")
    public void remove(CurrentUser user) {
        contacts.remove(user.userId());
    }

    @ExceptionHandler(PhoneVerifications.TooManyCodesException.class)
    public ResponseEntity<Map<String, String>> tooManyCodes(PhoneVerifications.TooManyCodesException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.retryAfterSeconds()))
                .body(Map.of("error", exception.getMessage()));
    }
}
