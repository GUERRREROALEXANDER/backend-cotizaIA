package com.cotizaia.agent.pipeline;

/** Identifies one question for a reviewer or client without storing a business row. */
public record AmbiguityFinding(String code, String question, boolean blocking) {
}
