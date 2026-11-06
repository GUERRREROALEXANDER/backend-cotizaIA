package com.cotizaia.agent.embedding;

/** Why a text is embedded; Voyage treats queries and documents asymmetrically. */
public enum EmbeddingPurpose {
    QUERY,
    DOCUMENT
}
