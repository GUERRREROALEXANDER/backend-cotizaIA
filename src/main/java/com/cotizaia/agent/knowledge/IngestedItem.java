package com.cotizaia.agent.knowledge;

import java.math.BigDecimal;

public record IngestedItem(long chunkId, String requirementType, BigDecimal hours, int page) {
}