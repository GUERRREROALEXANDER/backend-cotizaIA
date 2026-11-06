package com.cotizaia.agent.knowledge;

import com.cotizaia.domain.ChunkKind;
import java.util.ArrayList;
import java.util.List;

/**
 * Chunker for an approved proposal: one priced ITEM chunk per human-approved line, with no passages because the
 * brief text is already part of each item's query-shaped text.
 */
public class QuotedItemChunker implements Chunker {

    @Override
    public List<ChunkDraft> chunk(IngestionSource source) {
        List<ChunkDraft> drafts = new ArrayList<>();
        List<ApprovedItem> items = source.approvedItems() == null ? List.of() : source.approvedItems();
        for (ApprovedItem item : items) {
            if (item == null || item.text() == null || item.text().isBlank() || item.hours() == null) {
                continue;
            }
            drafts.add(new ChunkDraft(ChunkKind.ITEM, item.text(), null, item.requirementType(), item.hours()));
        }
        return drafts;
    }
}
