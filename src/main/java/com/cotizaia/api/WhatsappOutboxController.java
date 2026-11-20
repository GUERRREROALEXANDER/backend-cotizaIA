package com.cotizaia.api;

import com.cotizaia.service.whatsapp.WhatsappOutbox;
import com.cotizaia.service.whatsapp.WhatsappServiceKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The WhatsApp service's side of the outbox: claim the next messages, then report each one sent or failed.
 * It authenticates with the shared {@code X-API-Key} (WHATSAPP_SERVICE_KEY), not a user session; while no key is
 * configured every call answers 503, so the queue cannot be drained by anyone.
 */
@RestController
@RequestMapping("/api/whatsapp/outbox")
@Tag(name = "WhatsApp service")
public class WhatsappOutboxController {

    private final WhatsappOutbox outbox;
    private final WhatsappServiceKey serviceKey;

    public WhatsappOutboxController(WhatsappOutbox outbox, WhatsappServiceKey serviceKey) {
        this.outbox = outbox;
        this.serviceKey = serviceKey;
    }

    public record OutgoingMessage(Long id, String phone, String body) {
    }

    public record Batch(List<OutgoingMessage> items) {
    }

    public record FailureRequest(
            @NotBlank @Size(max = 200) @Pattern(regexp = "^(?!.*://).*$", message = "must not contain URLs")
            String error) {
    }

    @PostMapping("/claim")
    @Operation(summary = "Next messages to send; one not reported within a minute is offered again, at most 3 times")
    public Batch claim(@Parameter(hidden = true) @RequestHeader(value = "X-API-Key", required = false) String key,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        serviceKey.require(key);
        return new Batch(outbox.claim(limit).stream()
                .map(message -> new OutgoingMessage(message.getId(), message.getPhone(), message.getBody()))
                .toList());
    }

    @PostMapping("/{id}/sent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Report a message as delivered to WhatsApp")
    public void sent(@Parameter(hidden = true) @RequestHeader(value = "X-API-Key", required = false) String key,
            @PathVariable Long id) {
        serviceKey.require(key);
        requireFound(outbox.finish(id, null), id);
    }

    @PostMapping("/{id}/failed")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Give up on a message that retrying cannot fix, such as a number without WhatsApp")
    public void failed(@Parameter(hidden = true) @RequestHeader(value = "X-API-Key", required = false) String key,
            @PathVariable Long id, @Valid @RequestBody FailureRequest request) {
        serviceKey.require(key);
        requireFound(outbox.finish(id, request.error()), id);
    }

    private static void requireFound(boolean found, Long id) {
        if (!found) {
            throw new NoSuchElementException("No message with id " + id);
        }
    }

    @ExceptionHandler(WhatsappServiceKey.RefusedException.class)
    public ResponseEntity<Map<String, String>> refused(WhatsappServiceKey.RefusedException exception) {
        return ResponseEntity.status(exception.status()).body(Map.of("error", exception.getMessage()));
    }
}
