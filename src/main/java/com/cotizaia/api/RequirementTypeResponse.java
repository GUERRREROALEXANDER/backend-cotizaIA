package com.cotizaia.api;

import com.cotizaia.domain.RequirementType;
import java.math.BigDecimal;

/** Public projection without persistence internals. */
public record RequirementTypeResponse(Long id, String name, String description, BigDecimal estimatedHours) {

    public static RequirementTypeResponse from(RequirementType type) {
        return new RequirementTypeResponse(type.getId(), type.getName(), type.getDescription(), type.getEstimatedHours());
    }
}
