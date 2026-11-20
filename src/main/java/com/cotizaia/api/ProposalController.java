package com.cotizaia.api;

import com.cotizaia.document.DocumentStats;
import com.cotizaia.document.DocumentType;
import com.cotizaia.domain.ProposalDocument;
import com.cotizaia.domain.ProposalStatus;
import com.cotizaia.domain.spec.ReadinessReport;
import com.cotizaia.service.ApprovalQueueService;
import com.cotizaia.service.DocumentService;
import com.cotizaia.service.ProposalInsightService;
import com.cotizaia.service.ProposalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    private final ProposalInsightService insights;

    public ProposalController(ProposalService proposals, ApprovalQueueService approval, DocumentService documents,
            ProposalInsightService insights) {
        this.proposals = proposals;
        this.approval = approval;
        this.documents = documents;
        this.insights = insights;
    }

    @GetMapping(value = "/{id}/draft/markdown", produces = "text/markdown;charset=UTF-8")
    @Operation(summary = "Export the drafted proposal text as Markdown (Visitor over the document tree)")
    public ResponseEntity<String> draftMarkdown(@PathVariable Long id, CurrentUser user) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/markdown;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("propuesta-" + id + ".md").build().toString())
                .body(insights.draftMarkdown(user.agencyId(), id));
    }

    @GetMapping("/{id}/draft/stats")
    @Operation(summary = "Measure the drafted proposal: sections, words, cited sources, reading time")
    public DocumentStats draftStats(@PathVariable Long id, CurrentUser user) {
        return insights.draftStats(user.agencyId(), id);
    }

    @GetMapping("/{id}/readiness")
    @Operation(summary = "Approval checklist: which business rules the proposal meets (Specification)")
    public ReadinessReport readiness(@PathVariable Long id, CurrentUser user) {
        return insights.readiness(user.agencyId(), id);
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

    @PostMapping("/{id}/extras")
    @Operation(summary = "Apply a price extra (URGENCY, DISCOUNT or EXTENDED_WARRANTY)")
    public ProposalDetailResponse addExtra(@PathVariable Long id, @Valid @RequestBody AddExtraRequest request,
            CurrentUser user) {
        return ProposalDetailResponse.from(proposals.addExtra(user.agencyId(), id, request.type()));
    }

    @DeleteMapping("/{id}/extras/{extraId}")
    @Operation(summary = "Remove an applied price extra")
    public ProposalDetailResponse removeExtra(@PathVariable Long id, @PathVariable Long extraId, CurrentUser user) {
        return ProposalDetailResponse.from(proposals.removeExtra(user.agencyId(), id, extraId));
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

    @PostMapping("/{id}/viewed")
    @Operation(summary = "Mark a sent offer as viewed (tracked fact 'vista', no status change)")
    public ProposalDetailResponse viewed(@PathVariable Long id, CurrentUser user) {
        return ProposalDetailResponse.from(proposals.viewed(user.agencyId(), id));
    }

    @PostMapping("/{id}/negotiate")
    @Operation(summary = "Negotiate proposal")
    public ProposalDetailResponse negotiate(@PathVariable Long id, CurrentUser user) {
        return ProposalDetailResponse.from(proposals.negotiate(user.agencyId(), id));
    }

    @PostMapping("/{id}/revise")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Issue the next revision of a negotiated proposal")
    public ProposalDetailResponse revise(@PathVariable Long id, CurrentUser user) {
        return ProposalDetailResponse.from(proposals.revise(user.agencyId(), id));
    }

    @PostMapping("/{id}/undo")
    @Operation(summary = "Undo the latest quote command")
    public ProposalDetailResponse undo(@PathVariable Long id, CurrentUser user) {
        return ProposalDetailResponse.from(proposals.undo(user.agencyId(), id));
    }

    @GetMapping("/{id}/commands")
    @Operation(summary = "List executed quote commands")
    public List<ProposalCommandResponse> commands(@PathVariable Long id, CurrentUser user) {
        return proposals.commands(user.agencyId(), id).stream().map(ProposalCommandResponse::from).toList();
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
