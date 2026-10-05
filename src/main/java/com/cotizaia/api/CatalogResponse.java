package com.cotizaia.api;

import com.cotizaia.domain.ServiceCatalog;
import java.util.List;

/** Public projection without persistence internals. */
public record CatalogResponse(Long id, String name, String description, Long categoryId,
        List<RequirementTypeResponse> requirementTypes) {

    public static CatalogResponse from(ServiceCatalog catalog) {
        return new CatalogResponse(catalog.getId(), catalog.getName(), catalog.getDescription(),
                catalog.getCategory() == null ? null : catalog.getCategory().getId(),
                catalog.getRequirementTypes().stream().map(RequirementTypeResponse::from).toList());
    }
}
