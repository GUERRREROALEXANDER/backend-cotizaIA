package com.cotizaia.api;

import com.cotizaia.domain.Brief;
import com.cotizaia.domain.BriefAttachment;
import com.cotizaia.facade.QuotationFacade;
import com.cotizaia.service.BriefDetailService;
import com.cotizaia.service.BriefService;
import com.cotizaia.service.BriefUploadService;
import com.cotizaia.service.BriefUploadService.UploadResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Brief ingest API. {@code POST /api/briefs} accepts any supported channel;
 * channel-specific parsing happens behind the {@code BriefSource} port.
 */
@RestController
@RequestMapping("/api/briefs")
@Tag(name = "Briefs")
public class BriefController {

    private final BriefService briefService;

    private final BriefDetailService details;

    private final QuotationFacade facade;

    private final BriefUploadService uploads;

    public BriefController(BriefService briefService, BriefDetailService details, QuotationFacade facade,
            BriefUploadService uploads) {
        this.briefService = briefService;
        this.details = details;
        this.facade = facade;
        this.uploads = uploads;
    }

    @PostMapping
    @Operation(summary = "Ingest a brief")
    @ResponseStatus(HttpStatus.CREATED)
    public BriefResponse ingest(@Valid @RequestBody BriefIngestRequest request, CurrentUser user) {
        Brief brief = briefService.ingest(user.agencyId(), request.source(), request.clientId(), request.payload());
        return BriefResponse.from(brief);
    }

    @GetMapping
    @Operation(summary = "List the caller's briefs, newest first")
    public List<BriefResponse> list(
            @RequestParam(defaultValue = "35") @Min(1) @Max(100) int limit, CurrentUser user) {
        return briefService.list(user.agencyId(), limit).stream().map(BriefResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get brief details")
    public BriefDetailResponse get(@PathVariable Long id, CurrentUser user) {
        return BriefDetailResponse.from(details.get(user.agencyId(), id));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Ingest a brief from an uploaded RFP PDF")
    @ResponseStatus(HttpStatus.CREATED)
    public BriefUploadResponse upload(@RequestParam("file") MultipartFile file,
            @RequestParam("clientId") Long clientId,
            @RequestParam(value = "note", required = false) String note, CurrentUser user) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read uploaded file", e);
        }
        UploadResult result =
                uploads.upload(user.agencyId(), clientId, file.getOriginalFilename(), bytes, note);
        return new BriefUploadResponse(BriefResponse.from(result.brief()), result.fileName(), result.pages(),
                result.truncated(), result.unreadablePages());
    }

    @GetMapping(value = "/{id}/attachment", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download the uploaded RFP PDF")
    public ResponseEntity<byte[]> attachment(@PathVariable Long id, CurrentUser user) {
        BriefAttachment attachment = uploads.attachmentFor(user.agencyId(), id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(attachment.getFileName(), StandardCharsets.UTF_8).build().toString())
                .body(attachment.getContent());
    }

    @PostMapping("/{id}/process")
    @Operation(summary = "Process an existing brief")
    public QuotationResponse process(@PathVariable Long id, CurrentUser user) {
        return QuotationResponse.from(facade.processBrief(user.agencyId(), id));
    }

    @PostMapping("/{id}/questions/{questionId}/answer")
    @Operation(summary = "Answer a question and reprocess the brief")
    public QuotationResponse answer(@PathVariable Long id, @PathVariable Long questionId,
            @Valid @RequestBody AnswerQuestionRequest request, CurrentUser user) {
        return QuotationResponse.from(facade.answerQuestion(user.agencyId(), id, questionId, request.answer()));
    }
}
