package com.cotizaia.agent.pipeline;

/** Where a requirement's hours came from, in decreasing order of trust. */
public enum EstimateSource {
    MEMORY,
    CATALOG,
    DEFAULT;

    /** Stored enum name to enum, tolerating null or legacy values. */
    public static EstimateSource fromStored(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return valueOf(name);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
