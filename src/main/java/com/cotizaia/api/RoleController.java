package com.cotizaia.api;

import com.cotizaia.service.RateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes agency-scoped roles and the rates nested under each role (project.txt section 2).
 * Application services own persistence and rules so HTTP mapping stays independent of the domain workflow.
 */
@RestController
@RequestMapping("/api/roles")
@Tag(name = "Roles")
public class RoleController {

    private final RateService rates;

    public RoleController(RateService rates) {
        this.rates = rates;
    }

    @GetMapping
    @Operation(summary = "List roles")
    public List<RoleResponse> roles(CurrentUser user) {
        return rates.listRoles(user.agencyId()).stream().map(RoleResponse::from).toList();
    }

    @PostMapping
    @Operation(summary = "Create role")
    @ResponseStatus(HttpStatus.CREATED)
    public RoleResponse createRole(@Valid @RequestBody RoleRequest request, CurrentUser user) {
        return RoleResponse.from(rates.createRole(user.agencyId(), request.name()));
    }

    @DeleteMapping("/{roleId}")
    @Operation(summary = "Delete role")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRole(@PathVariable Long roleId, CurrentUser user) {
        rates.deleteRole(user.agencyId(), roleId);
    }

    @GetMapping("/{roleId}/rates")
    @Operation(summary = "List role rates")
    public List<RateResponse> listRates(@PathVariable Long roleId, CurrentUser user) {
        return rates.listRates(user.agencyId(), roleId).stream().map(RateResponse::from).toList();
    }

    @PostMapping("/{roleId}/rates")
    @Operation(summary = "Create rate")
    @ResponseStatus(HttpStatus.CREATED)
    public RateResponse createRate(@PathVariable Long roleId, @Valid @RequestBody RateRequest request,
            CurrentUser user) {
        return RateResponse.from(rates.createRate(user.agencyId(), roleId, request.copPerHour().longValueExact(),
                request.effectiveFrom(), request.effectiveTo()));
    }
}
