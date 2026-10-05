package com.cotizaia.api;

import com.cotizaia.service.AgencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes agency-scoped agency operations (project.txt section 2).
 * Application services own persistence and rules so HTTP mapping stays independent of the domain workflow.
 */
@RestController
@RequestMapping("/api/agency")
@Tag(name = "Agency")
public class AgencyController {

    private final AgencyService agencies;

    public AgencyController(AgencyService agencies) {
        this.agencies = agencies;
    }

    @GetMapping("/pricing-model")
    @Operation(summary = "Get default pricing model")
    public PricingModelResponse pricingModel(CurrentUser user) {
        return PricingModelResponse.from(agencies.getPricingModel(user.agencyId()));
    }

    @PutMapping("/pricing-model")
    @Operation(summary = "Set default pricing model")
    public PricingModelResponse updatePricingModel(@Valid @RequestBody PricingModelRequest request,
            CurrentUser user) {
        return PricingModelResponse.from(agencies.updatePricingModel(user.agencyId(), request.pricingModel()));
    }
}
