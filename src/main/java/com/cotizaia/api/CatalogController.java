package com.cotizaia.api;

import com.cotizaia.service.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes agency-scoped catalog operations (project.txt section 2).
 * Application services own persistence and rules so HTTP mapping stays independent of the domain workflow.
 */
@RestController
@RequestMapping("/api/catalog")
@Tag(name = "Catalog")
public class CatalogController {

    private final CatalogService catalogs;

    public CatalogController(CatalogService catalogs) {
        this.catalogs = catalogs;
    }

    @GetMapping
    @Operation(summary = "List catalog entries")
    public List<CatalogResponse> list(CurrentUser user) {
        return catalogs.list(user.agencyId()).stream().map(CatalogResponse::from).toList();
    }

    @PostMapping
    @Operation(summary = "Create catalog entry")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogResponse create(@Valid @RequestBody CatalogRequest request, CurrentUser user) {
        return CatalogResponse.from(catalogs.create(user.agencyId(), request.name(),
                request.description(), request.categoryId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update catalog entry")
    public CatalogResponse update(@PathVariable Long id, @Valid @RequestBody CatalogRequest request,
            CurrentUser user) {
        return CatalogResponse.from(catalogs.update(user.agencyId(), id, request.name(),
                request.description(), request.categoryId()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete catalog entry")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, CurrentUser user) {
        catalogs.delete(user.agencyId(), id);
    }

    @PostMapping("/{catalogId}/requirement-types")
    @Operation(summary = "Create requirement type")
    @ResponseStatus(HttpStatus.CREATED)
    public RequirementTypeResponse addType(@PathVariable Long catalogId,
            @Valid @RequestBody RequirementTypeRequest request, CurrentUser user) {
        return RequirementTypeResponse.from(catalogs.addType(user.agencyId(), catalogId,
                request.name(), request.description(), request.estimatedHours()));
    }

    @PutMapping("/{catalogId}/requirement-types/{typeId}")
    @Operation(summary = "Update requirement type")
    public RequirementTypeResponse updateType(@PathVariable Long catalogId, @PathVariable Long typeId,
            @Valid @RequestBody RequirementTypeRequest request, CurrentUser user) {
        return RequirementTypeResponse.from(catalogs.updateType(user.agencyId(), catalogId, typeId,
                request.name(), request.description(), request.estimatedHours()));
    }

    @DeleteMapping("/{catalogId}/requirement-types/{typeId}")
    @Operation(summary = "Delete requirement type")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteType(@PathVariable Long catalogId, @PathVariable Long typeId, CurrentUser user) {
        catalogs.deleteType(user.agencyId(), catalogId, typeId);
    }
}
