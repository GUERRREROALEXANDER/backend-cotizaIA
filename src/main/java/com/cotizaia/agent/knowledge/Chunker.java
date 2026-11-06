package com.cotizaia.agent.knowledge;

import java.util.List;

/** Turns an {@link IngestionSource} into chunk drafts. */
public interface Chunker {

    List<ChunkDraft> chunk(IngestionSource source);

    /** Number of items rejected during the last {@link #chunk} call. */
    default int rejected() {
        return 0;
    }
}