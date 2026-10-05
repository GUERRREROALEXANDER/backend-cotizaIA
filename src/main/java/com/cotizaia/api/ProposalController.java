package com.cotizaia.api;

import com.cotizaia.document.DocumentType;
import com.cotizaia.domain.ProposalDocument;
import com.cotizaia.domain.ProposalStatus;
import com.cotizaia.service.ApprovalQueueService;
import com.cotizaia.service.DocumentService;
import com.cotizaia.service.ProposalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the human proposal workflow and persisted documents (project.txt sections 2, 4 and 5).
 * Each endpoint delegates one application operation so state decisions remain in the domain and services.
 */
@RestController
@RequestMapping("/api/proposals")
@Tag(name = "Proposals")
public class ProposalController {

    private final ProposalService proposals;

    private final ApprovalQueueService approval;

    private final DocumentService documents;

    public ProposalController(ProposalService proposals, ApprovalQueueService approval, DocumentService documents) {
        this.proposals = proposals;
        this.approval = approval;
        this.documents = documents;
    }

    @GetMapping
    @Operation(summary = "List agency proposals")
    public List<ProposalResponse> list(@RequestParam(required = false) ProposalStatus status, CurrentUser user) {
        return proposals.list(user.agencyId(), status).stream().map(ProposalResponse::from).toList();
    }

    @GetMapping("/queue")
    @Operation(summary = "List proposals awaiting review")
    public List<ProposalResponse> queue(CurrentUser user) {
        return proposals.queue(user.agencyId()).stream().map(ProposalResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get proposal details")
    public ProposalDetailResponse get(@PathVariable Long id, CurrentUser user) {
        return ProposalDetailResponse.from(proposals.get(user.agencyId(), id));
    }

    @PutMapping("/{id}/items/{itemId}")
    @Operation(summary = "Adjust quoted hours")
    public ProposalDetailResponse adjustHours(@PathVariable Long id, @PathVariable Long itemId,
            @Valid @RequestBody AdjustHoursRequest request, CurrentUser user) {
        return ProposalDetailResponse.from(proposals.adjustHours(user.agencyId(), id, itemId, request.hours()));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve proposal")
    public ProposalDetailResponse approve(@PathVariable Long id, CurrentUser user) {
        return ProposalDetailResponse.from(proposals.approve(user.agencyId(), id));
    }

    @PostMapping("/{id}/send")
    @Operation(summary = "Send proposal")
    public ProposalDetailResponse send(@PathVariable Long id, CurrentUser user) {
        return ProposalDetailResponse.from(proposals.send(user.agencyId(), id));
    }

    @PostMapping("/{id}/negotiate")
    @Operation(summary = "Negotiate proposal")
    public ProposalDetailResponse negotiate(@PathVariable Long id, CurrentUser user) {
        return ProposalDetailResponse.from(proposals.negotiate(user.agencyId(), id));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject proposal")
    public ProposalDetailResponse reject(@PathVariable Long id, CurrentUser user) {
        return ProposalDetailResponse.from(proposals.reject(user.agencyId(), id));
    }

    @PostMapping("/{id}/expire")
    @Operation(summary = "Expire proposal")
    public ProposalDetailResponse expire(@PathVariable Long id, CurrentUser user) {
        return ProposalDetailResponse.from(proposals.expire(user.agencyId(), id));
    }

    @PostMapping("/{id}/accept")
    @Operation(summary = "Accept proposal with a simulated deposit")
    public AcceptanceResponse accept(@PathVariable Long id, CurrentUser user) {
        return AcceptanceResponse.from(approval.accept(user.agencyId(), id));
    }

    @GetMapping("/{id}/documents")
    @Operation(summary = "List proposal documents")
    public List<DocumentResponse> documents(@PathVariable Long id, CurrentUser user) {
        return documents.list(user.agencyId(), id).stream().map(DocumentResponse::from).toList();
    }

    @GetMapping(value = "/{id}/documents/{type}", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download a proposal PDF")
    public ResponseEntity<byte[]> download(@PathVariable Long id, @PathVariable DocumentType type, CurrentUser user) {
        ProposalDocument document = documents.get(user.agencyId(), id, type);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(document.getFileName()).build().toString())
                .body(document.getContent());
    }
}
