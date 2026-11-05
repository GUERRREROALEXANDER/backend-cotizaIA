package com.cotizaia.api;

import com.cotizaia.service.RateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes operations on an existing hourly rate (project.txt section 2).
 * Rates are created under their role in {@link RoleController}; here they are addressed by their own id.
 */
@RestController
@RequestMapping("/api/rates")
@Tag(name = "Rates")
public class RateController {

    private final RateService rates;

    public RateController(RateService rates) {
        this.rates = rates;
    }

    @PutMapping("/{rateId}")
    @Operation(summary = "Update rate")
    public RateResponse updateRate(@PathVariable Long rateId, @Valid @RequestBody UpdateRateRequest request,
            CurrentUser user) {
        return RateResponse.from(rates.updateRate(user.agencyId(), rateId, request.copPerHour().longValueExact()));
    }

    @DeleteMapping("/{rateId}")
    @Operation(summary = "Delete rate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRate(@PathVariable Long rateId, CurrentUser user) {
        rates.deleteRate(user.agencyId(), rateId);
    }
}
