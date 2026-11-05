package com.cotizaia.agent.knowledge;

import com.cotizaia.domain.ChunkKind;
import java.math.BigDecimal;

/** A chunk that has been prepared for persistence but not yet saved. */
public record ChunkDraft(ChunkKind kind, String text, Integer page, String requirementType, BigDecimal hours) {
}