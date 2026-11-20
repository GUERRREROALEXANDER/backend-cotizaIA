package com.cotizaia.api;

import com.cotizaia.agent.whatsapp.BotReply;
import com.cotizaia.agent.whatsapp.WhatsappInbox;
import com.cotizaia.document.DocumentType;
import com.cotizaia.domain.AppUser;
import com.cotizaia.domain.ProposalDocument;
import com.cotizaia.service.DocumentService;
import com.cotizaia.service.whatsapp.WhatsappServiceKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
    private final DocumentService documents;

    public WhatsappBotController(WhatsappInbox inbox, WhatsappServiceKey serviceKey, DocumentService documents) {
        this.inbox = inbox;
        this.serviceKey = serviceKey;
        this.documents = documents;
    }

    /**
     * {@code from} is the sender's WhatsApp address (chat memory and reply limit); {@code phone} is the sender's
     * number as WhatsApp reports it, when it does, and is what recognizes a verified agency user.
     */
    public record IncomingMessage(@NotBlank @Size(max = 100) String from,
            @Pattern(regexp = "\\+?[1-9]\\d{7,14}", message = "must be a phone number with country code") String phone,
            @NotBlank @Size(max = 2000) String text) {
    }

    /**
     * {@code sticker} names a robot sticker the service sends before the text (saludo, pensando, listo, cotizar);
     * {@code documents} are the PDFs it sends after the text, each fetched from {@code POST /document}.
     */
    public record Reply(String reply, String sticker, List<BotReply.Document> documents) {
    }

    /** {@code phone} is the number that asked: only a verified user of the proposal's agency gets its PDF. */
    public record DocumentRequest(
            @NotBlank @Pattern(regexp = "\\+?[1-9]\\d{7,14}", message = "must be a phone number with country code") String phone,
            @NotNull Long proposalId, @NotNull DocumentType type) {
    }

    @PostMapping("/reply")
    @Operation(summary = "The reply to a message received on the business number (null to stay quiet)")
    public Reply reply(@Parameter(hidden = true) @RequestHeader(value = "X-API-Key", required = false) String key,
            @Valid @RequestBody IncomingMessage message) {
        serviceKey.require(key);
        return inbox.reply(message.from(), message.phone(), message.text().trim())
                .map(answer -> new Reply(answer.text(), answer.sticker() == null ? null : answer.sticker().fileName(),
                        answer.documents()))
                .orElse(new Reply(null, null, List.of()));
    }

    @PostMapping(value = "/document", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "A proposal PDF for the WhatsApp service to send to the agency user who asked")
    public ResponseEntity<byte[]> document(
            @Parameter(hidden = true) @RequestHeader(value = "X-API-Key", required = false) String key,
            @Valid @RequestBody DocumentRequest request) {
        serviceKey.require(key);
        // Unknown number and another agency's proposal answer the same 404: the service learns nothing either way.
        AppUser staff = inbox.staff(request.phone())
                .orElseThrow(() -> new NoSuchElementException("Proposal not found: " + request.proposalId()));
        ProposalDocument document = documents.get(staff.getAgency().getId(), request.proposalId(), request.type());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(document.getFileName()).build().toString())
                .body(document.getContent());
    }

    @ExceptionHandler(WhatsappServiceKey.RefusedException.class)
    public ResponseEntity<Map<String, String>> refused(WhatsappServiceKey.RefusedException exception) {
        return ResponseEntity.status(exception.status()).body(Map.of("error", exception.getMessage()));
    }
}
