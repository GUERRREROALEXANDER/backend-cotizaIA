package com.cotizaia.api;

import com.cotizaia.domain.Brief;
import com.cotizaia.facade.QuotationFacade;
import com.cotizaia.service.BriefDetailService;
import com.cotizaia.service.BriefService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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

    public BriefController(BriefService briefService, BriefDetailService details, QuotationFacade facade) {
        this.briefService = briefService;
        this.details = details;
        this.facade = facade;
    }

    @PostMapping
    @Operation(summary = "Ingest a brief")
    @ResponseStatus(HttpStatus.CREATED)
    public BriefResponse ingest(@Valid @RequestBody BriefIngestRequest request, CurrentUser user) {
        Brief brief = briefService.ingest(user.agencyId(), request.source(), request.clientId(), request.payload());
        return BriefResponse.from(brief);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get brief details")
    public BriefDetailResponse get(@PathVariable Long id, CurrentUser user) {
        return BriefDetailResponse.from(details.get(user.agencyId(), id));
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
