package com.cotizaia.agent.pipeline;

/** Carries a handler decision and a bounded human-readable log summary. */
public record HandlerResult(HandlerDecision decision, String summary) {

    public static HandlerResult proceed(String summary) {
        return new HandlerResult(HandlerDecision.CONTINUE, summary);
    }

    public static HandlerResult flag(String summary) {
        return new HandlerResult(HandlerDecision.FLAG_FOR_HUMAN, summary);
    }

    public static HandlerResult halt(String summary) {
        return new HandlerResult(HandlerDecision.HALT, summary);
    }
}
