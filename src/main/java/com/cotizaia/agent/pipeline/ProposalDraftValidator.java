package com.cotizaia.agent.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Guardrail between model text and a client-facing PDF (spec 05): money, hours, invented numbers and
 * citations outside the retrieved passages never pass. Empty list means the draft is safe to use.
 */
@Component
public class ProposalDraftValidator {

    private static final int MAX_TEXT = 600;

    private static final int MAX_LIST_ENTRIES = 6;

    /** Currencies and amounts: forbidden in every section. */
    private static final Pattern CURRENCY = Pattern.compile("\\$|\\b(cop|usd|pesos?)\\b", Pattern.CASE_INSENSITIVE);

    /**
     * Price vocabulary: forbidden in scope and assumptions (a promise about price). An exclusion may name a cost
     * concept ("no se incluyen costos de transacción") as long as it carries no number: found in production, where
     * the blanket rule rejected a legitimate exclusion twice and sent the draft to the template.
     */
    private static final Pattern PRICE_WORDS = Pattern.compile("\\b(precios?|costos?|valor(?:es)?|tarifas?)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DIGIT = Pattern.compile("\\d");

    private static final Pattern HOURS = Pattern.compile("\\b\\d[\\d.,]*\\s*(?:horas?|h)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)*");

    public List<String> violations(JsonNode draft, String briefText, List<String> requirementNames,
            Set<Long> allowedChunkIds) {
        List<String> violations = new ArrayList<>();
        if (draft == null || !draft.isObject()) {
            violations.add("draft must be a JSON object");
            return violations;
        }
        JsonNode scope = array(draft, "scope", violations);
        JsonNode assumptions = array(draft, "assumptions", violations);
        JsonNode exclusions = array(draft, "exclusions", violations);
        if (scope != null) {
            validateEntries(scope, "scope", briefText, allowedChunkIds, violations);
            validateCoverage(scope, requirementNames, violations);
        }
        if (assumptions != null) {
            validateEntries(assumptions, "assumption", briefText, allowedChunkIds, violations);
            validateCount(assumptions, "assumptions", violations);
        }
        if (exclusions != null) {
            validateEntries(exclusions, "exclusion", briefText, allowedChunkIds, violations);
            validateCount(exclusions, "exclusions", violations);
        }
        return violations;
    }

    private JsonNode array(JsonNode draft, String name, List<String> violations) {
        JsonNode node = draft.get(name);
        if (node == null || !node.isArray()) {
            violations.add("draft must contain a '" + name + "' array");
            return null;
        }
        return node;
    }

    private void validateCount(JsonNode entries, String name, List<String> violations) {
        if (entries.isEmpty() || entries.size() > MAX_LIST_ENTRIES) {
            violations.add(name + " must contain between 1 and " + MAX_LIST_ENTRIES + " entries");
        }
    }

    private void validateEntries(JsonNode entries, String kind, String briefText, Set<Long> allowedChunkIds,
            List<String> violations) {
        for (JsonNode entry : entries) {
            if (!entry.isObject()) {
                violations.add(kind + " entry must be a JSON object");
                continue;
            }
            JsonNode text = entry.get("text");
            if (text == null || !text.isTextual() || text.asText().isBlank()) {
                violations.add(kind + " entry needs a non-blank text");
            } else {
                validateText(text.asText(), kind, briefText, violations);
            }
            JsonNode citations = entry.get("citations");
            if (citations == null || !citations.isArray()) {
                violations.add(kind + " entry needs a citations array");
            } else {
                validateCitations(citations, kind, allowedChunkIds, violations);
            }
        }
    }

    private void validateText(String text, String kind, String briefText, List<String> violations) {
        if (text.length() > MAX_TEXT) {
            violations.add(kind + " text exceeds " + MAX_TEXT + " characters");
        }
        boolean priceWords = PRICE_WORDS.matcher(text).find();
        boolean exclusionWithoutAmount = "exclusion".equals(kind) && !DIGIT.matcher(text).find();
        if (CURRENCY.matcher(text).find() || (priceWords && !exclusionWithoutAmount)) {
            violations.add(kind + " text mentions money: " + text);
        }
        if (HOURS.matcher(text).find()) {
            violations.add(kind + " text mentions hours: " + text);
        }
        Set<String> grounded = numbers(briefText);
        Matcher numbers = NUMBER.matcher(text);
        while (numbers.find()) {
            String number = numbers.group();
            if (!grounded.contains(digits(number))) {
                violations.add(kind + " text contains a number not present in the brief: " + number);
            }
        }
    }

    private void validateCitations(JsonNode citations, String kind, Set<Long> allowedChunkIds,
            List<String> violations) {
        for (JsonNode citation : citations) {
            Long id = citationId(citation);
            if (id == null) {
                violations.add(kind + " entry has a citation that is not a chunk id: " + citation);
            } else if (allowedChunkIds == null || !allowedChunkIds.contains(id)) {
                violations.add(kind + " entry cites a passage that was not retrieved: " + id);
            }
        }
    }

    private Long citationId(JsonNode citation) {
        if (citation.isIntegralNumber()) {
            return citation.asLong();
        }
        if (citation.isTextual()) {
            try {
                return Long.valueOf(citation.asText().trim());
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        return null;
    }

    private void validateCoverage(JsonNode scope, List<String> requirementNames, List<String> violations) {
        List<String> expected = requirementNames.stream().map(ProposalDraftValidator::normalize).toList();
        List<String> declared = new ArrayList<>();
        for (JsonNode entry : scope) {
            JsonNode requirement = entry.isObject() ? entry.get("requirement") : null;
            String name = requirement != null && requirement.isTextual() ? requirement.asText() : "";
            declared.add(normalize(name));
            if (!expected.contains(normalize(name))) {
                violations.add("scope has no matching requirement: " + (name.isBlank() ? "(blank)" : name));
            }
        }
        for (int index = 0; index < expected.size(); index++) {
            String name = requirementNames.get(index);
            int matches = 0;
            for (String candidate : declared) {
                if (candidate.equals(expected.get(index))) {
                    matches++;
                }
            }
            if (matches != 1) {
                violations.add("scope must contain exactly one entry for requirement '" + name + "'");
            }
        }
    }

    private static Set<String> numbers(String text) {
        Set<String> values = new HashSet<>();
        Matcher matcher = NUMBER.matcher(text == null ? "" : text);
        while (matcher.find()) {
            values.add(digits(matcher.group()));
        }
        return values;
    }

    private static String digits(String number) {
        return number.replace(".", "").replace(",", "");
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .trim().toLowerCase(Locale.ROOT);
    }
}
