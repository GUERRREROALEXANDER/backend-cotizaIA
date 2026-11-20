package com.cotizaia.api;

import com.cotizaia.service.whatsapp.InstantQuoteService;
import com.cotizaia.service.whatsapp.WhatsappServiceKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
 * The bot's brain, called by the WhatsApp service for every message a person writes to the business number. It
 * answers with the text to send back (an approximate quote and the agency's link), or a null reply to stay quiet.
 */
@RestController
@RequestMapping("/api/whatsapp/bot")
@Tag(name = "WhatsApp service")
public class WhatsappBotController {

    private final InstantQuoteService quotes;
    private final WhatsappServiceKey serviceKey;

    public WhatsappBotController(InstantQuoteService quotes, WhatsappServiceKey serviceKey) {
        this.quotes = quotes;
        this.serviceKey = serviceKey;
    }

    /** {@code from} is the sender's WhatsApp address; it only keys the per-sender reply limit and is not stored. */
    public record IncomingMessage(@NotBlank @Size(max = 100) String from, @NotBlank @Size(max = 2000) String text) {
    }

    public record Reply(String reply) {
    }

    @PostMapping("/reply")
    @Operation(summary = "The bot's reply to a message received on the business number (reply is null to stay quiet)")
    public Reply reply(@Parameter(hidden = true) @RequestHeader(value = "X-API-Key", required = false) String key,
            @Valid @RequestBody IncomingMessage message) {
        serviceKey.require(key);
        return new Reply(quotes.reply(message.from(), message.text()).orElse(null));
    }

    @ExceptionHandler(WhatsappServiceKey.RefusedException.class)
    public ResponseEntity<Map<String, String>> refused(WhatsappServiceKey.RefusedException exception) {
        return ResponseEntity.status(exception.status()).body(Map.of("error", exception.getMessage()));
    }
}
