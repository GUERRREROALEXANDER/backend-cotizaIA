package com.cotizaia.agent.knowledge;

import com.cotizaia.domain.ChunkKind;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Chunker for a past proposal PDF: citable passages per page plus one chunk per
 * grounded line item.
 */
public class ProposalPdfChunker implements Chunker {

    public static final int MAX_PASSAGE_CHARS = 1200;

    private final PastItemExtractor extractor;

    private int rejectedItems;

    public ProposalPdfChunker(PastItemExtractor extractor) {
        this.extractor = extractor;
    }

    @Override
    public List<ChunkDraft> chunk(IngestionSource source) {
        rejectedItems = 0;
        List<ChunkDraft> drafts = new ArrayList<>();
        List<String> pages = source.pages() == null ? List.of() : source.pages();
        for (int i = 0; i < pages.size(); i++) {
            String pageText = pages.get(i);
            if (pageText == null || pageText.isBlank()) {
                continue;
            }
            int pageNumber = i + 1;
            for (String passage : splitPassages(pageText, MAX_PASSAGE_CHARS)) {
                if (!passage.isBlank()) {
                    drafts.add(new ChunkDraft(ChunkKind.PASSAGE, passage, pageNumber, null, null));
                }
            }
        }
        if (extractor != null) {
            PastItemExtractor.Result result = extractor.extract(pages, source.catalogNames());
            rejectedItems = result.rejected();
            for (PastItemExtractor.Item item : result.items()) {
                drafts.add(new ChunkDraft(ChunkKind.ITEM,
                        item.requirementType() + ": " + item.description(), item.page(),
                        item.requirementType(), item.hours()));
            }
        }
        return drafts;
    }

    @Override
    public int rejected() {
        return rejectedItems;
    }

    static List<String> splitPassages(String text, int max) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        String[] paragraphs = text.split("\\n\\s*\\n");
        List<String> current = new ArrayList<>();
        int currentLength = 0;
        for (String rawParagraph : paragraphs) {
            String paragraph = rawParagraph.strip();
            if (paragraph.isEmpty()) {
                continue;
            }
            if (paragraph.length() > max) {
                flushCurrent(result, current);
                current = new ArrayList<>();
                currentLength = 0;
                splitLongParagraph(result, paragraph, max);
                continue;
            }
            int added = paragraph.length() + (current.isEmpty() ? 0 : 2);
            if (!current.isEmpty() && currentLength + added > max) {
                flushCurrent(result, current);
                current = new ArrayList<>();
                currentLength = 0;
            }
            current.add(paragraph);
            currentLength += paragraph.length() + (current.size() > 1 ? 2 : 0);
        }
        flushCurrent(result, current);
        if (result.isEmpty()) {
            splitLongParagraph(result, text.strip(), max);
        }
        return result;
    }

    private static void flushCurrent(List<String> result, List<String> current) {
        if (current.isEmpty()) {
            return;
        }
        result.add(String.join("\n\n", current));
    }

    private static void splitLongParagraph(List<String> result, String paragraph, int max) {
        String[] words = paragraph.split("\\s+");
        StringBuilder chunk = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (chunk.length() > 0 && chunk.length() + 1 + word.length() > max) {
                result.add(chunk.toString());
                chunk.setLength(0);
            }
            if (chunk.length() > 0) {
                chunk.append(' ');
            }
            if (word.length() > max) {
                // hard split
                int start = 0;
                while (start < word.length()) {
                    int end = Math.min(start + max, word.length());
                    if (chunk.length() > 0) {
                        result.add(chunk.toString());
                        chunk.setLength(0);
                    }
                    result.add(word.substring(start, end));
                    start = end;
                }
            } else {
                chunk.append(word);
            }
        }
        if (chunk.length() > 0) {
            result.add(chunk.toString());
        }
    }
}