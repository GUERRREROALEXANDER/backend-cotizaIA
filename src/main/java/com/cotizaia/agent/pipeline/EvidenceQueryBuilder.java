package com.cotizaia.agent.pipeline;

import com.cotizaia.domain.RequirementType;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Turns a whole client brief into the one sentence-shaped query that describes a single requirement.
 *
 * <p>Retrieval is only useful when the query looks like the items stored in memory, so the brief is cut down to
 * the sentences that actually talk about the requirement type. Keywords come from the agency's own catalog entry
 * (its name plus the comma-separated terms in its description), which keeps the query aligned with how the agency
 * speaks and ignores the rest of the brief.
 */
@Component
public class EvidenceQueryBuilder {

    private static final int MAX_FALLBACK_CHARS = 1000;

    public String query(String briefText, RequirementType type) {
        String brief = briefText == null ? "" : briefText;
        String name = type == null || type.getName() == null ? "" : type.getName();
        List<String> keywords = keywords(type);
        List<String> kept = new ArrayList<>();
        for (String sentence : sentences(brief)) {
            String normalized = normalize(sentence);
            if (matchesAny(normalized, keywords)) {
                kept.add(sentence);
            }
        }
        if (kept.isEmpty()) {
            String capped = brief.length() > MAX_FALLBACK_CHARS ? brief.substring(0, MAX_FALLBACK_CHARS) : brief;
            return name + ": " + capped;
        }
        return name + ": " + String.join(" ", kept);
    }

    private List<String> keywords(RequirementType type) {
        Set<String> keywords = new LinkedHashSet<>();
        if (type == null) {
            return List.of();
        }
        if (type.getName() != null) {
            String name = normalize(type.getName());
            if (!name.isBlank()) {
                keywords.add(name);
            }
        }
        if (type.getDescription() != null) {
            for (String term : type.getDescription().split(",")) {
                String keyword = normalize(term);
                if (!keyword.isBlank()) {
                    keywords.add(keyword);
                }
            }
        }
        return List.copyOf(keywords);
    }

    private boolean matchesAny(String normalizedSentence, List<String> keywords) {
        for (String keyword : keywords) {
            if (keyword.length() >= 4 ? normalizedSentence.contains(keyword) : wholeWord(normalizedSentence, keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean wholeWord(String text, String keyword) {
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(keyword) + "(?![\\p{L}\\p{N}])")
                .matcher(text).find();
    }

    /** Splits on sentence terminators and line breaks, keeping the terminator with its sentence. */
    private List<String> sentences(String text) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '\n' || character == '\r') {
                flush(current, result);
                continue;
            }
            current.append(character);
            if (character == '.' || character == '!' || character == '?') {
                flush(current, result);
            }
        }
        flush(current, result);
        return result;
    }

    private void flush(StringBuilder current, List<String> result) {
        String sentence = current.toString().trim();
        current.setLength(0);
        if (!sentence.isEmpty()) {
            result.add(sentence);
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).trim();
    }
}
