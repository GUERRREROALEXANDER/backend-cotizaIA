package com.cotizaia.agent.knowledge;

import java.math.BigDecimal;
import java.util.List;

public record IngestionResult(long documentId, int passages, List<IngestedItem> items, int rejectedItems) {
}