package com.cotizaia.agent.pipeline;

/** Supplies a client's answer to a previous ambiguity code for a new pipeline pass. */
public record Clarification(String code, String question, String answer) {
}
