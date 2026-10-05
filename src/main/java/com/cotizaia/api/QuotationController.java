package com.cotizaia.api;

import com.cotizaia.facade.QuotationFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes agency-scoped quotations operations (project.txt section 2).
 * Application services own persistence and rules so HTTP mapping stays independent of the domain workflow.
 */
@RestController
@RequestMapping("/api/quotations")
@Tag(name = "Quotations")
public class QuotationController {

    private final QuotationFacade facade;

    public QuotationController(QuotationFacade facade) {
        this.facade = facade;
    }

    @PostMapping
    @Operation(summary = "Ingest and process a quotation")
    @ResponseStatus(HttpStatus.CREATED)
    public QuotationResponse create(@Valid @RequestBody BriefIngestRequest request, CurrentUser user) {
        return QuotationResponse.from(facade.ingestAndProcess(user.agencyId(), request.source(),
                request.clientId(), request.payload()));
    }
}
