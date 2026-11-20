package com.cotizaia.api;

import com.cotizaia.agent.whatsapp.WhatsappInbox;
import com.cotizaia.service.whatsapp.WhatsappServiceKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Called by the WhatsApp service for every message a person writes to the business number. A verified agency user
 * gets the quotation chat; anyone else the sales assistant. Answers with the text to send back, or null to stay quiet.
 */
@RestController
@RequestMapping("/api/whatsapp/bot")
@Tag(name = "WhatsApp service")
public class WhatsappBotController {

    private final WhatsappInbox inbox;
    private final WhatsappServiceKey serviceKey;

    public WhatsappBotController(WhatsappInbox inbox, WhatsappServiceKey serviceKey) {
        this.inbox = inbox;
        this.serviceKey = serviceKey;
    }

    /**
     * {@code from} is the sender's WhatsApp address (chat memory and reply limit); {@code phone} is the sender's
     * number as WhatsApp reports it, when it does, and is what recognizes a verified agency user.
     */
    public record IncomingMessage(@NotBlank @Size(max = 100) String from,
            @Pattern(regexp = "\\+?[1-9]\\d{7,14}", message = "must be a phone number with country code") String phone,
            @NotBlank @Size(max = 2000) String text) {
    }

    public record Reply(String reply) {
    }

    @PostMapping("/reply")
    @Operation(summary = "The reply to a message received on the business number (null to stay quiet)")
    public Reply reply(@Parameter(hidden = true) @RequestHeader(value = "X-API-Key", required = false) String key,
            @Valid @RequestBody IncomingMessage message) {
        serviceKey.require(key);
        return new Reply(inbox.reply(message.from(), message.phone(), message.text().trim()).orElse(null));
    }

    @ExceptionHandler(WhatsappServiceKey.RefusedException.class)
    public ResponseEntity<Map<String, String>> refused(WhatsappServiceKey.RefusedException exception) {
        return ResponseEntity.status(exception.status()).body(Map.of("error", exception.getMessage()));
    }
}
