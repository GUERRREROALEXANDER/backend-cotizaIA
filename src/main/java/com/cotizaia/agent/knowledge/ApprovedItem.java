package com.cotizaia.agent.knowledge;

import java.math.BigDecimal;

/**
 * One human-approved line item ready to become memory. The text is already shaped like the retrieval query the
 * estimate step will later send, so the approved proposal is comparable with new briefs.
 */
public record ApprovedItem(String requirementType, String text, BigDecimal hours) {
}
