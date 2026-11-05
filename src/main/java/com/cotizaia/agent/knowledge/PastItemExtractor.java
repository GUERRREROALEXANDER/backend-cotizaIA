package com.cotizaia.agent.knowledge;

import com.cotizaia.agent.llm.LlmClient;
import com.cotizaia.agent.llm.LlmRequest;
import com.cotizaia.agent.llm.LlmResponse;
import com.cotizaia.agent.llm.LlmTask;
import com.cotizaia.agent.llm.PromptBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Extracts priced line items from past proposal pages through an LLM, then
 * grounds every candidate against the agency catalog and the page text before
 * it is allowed to become memory.
 */
@org.springframework.stereotype.Component
public class PastItemExtractor {

    public record Item(String requirementType, String description, BigDecimal hours, int page) {
    }

    public record Result(List<Item> items, int rejected) {
    }

    private final LlmClient llm;
    private final PromptBuilder prompts;
    private final ObjectMapper mapper;

    public PastItemExtractor(LlmClient llm, PromptBuilder prompts, ObjectMapper mapper) {
        this.llm = llm;
        this.prompts = prompts;
        this.mapper = mapper;
    }

    public Result extract(List<String> pages, List<String> catalogNames) {
        List<Item> items = new ArrayList<>();
        int rejected = 0;
        Map<String, String> catalogByNorm = new HashMap<>();
        if (catalogNames != null) {
            for (String name : catalogNames) {
                if (name != null && !name.isBlank()) {
                    catalogByNorm.put(normalize(name), name.trim());
                }
            }
        }
        if (pages == null) {
            return new Result(items, rejected);
        }
        for (int i = 0; i < pages.size(); i++) {
            String page = pages.get(i);
            if (page == null || page.isBlank()) {
                continue;
            }
            int pageNumber = i + 1;
            LlmResponse response;
            try {
                LlmRequest request = prompts.extractItems(page, catalogNames == null ? List.of() : catalogNames);
                response = llm.complete(request);
            } catch (RuntimeException exception) {
                continue;
            }
            List<JsonNode> modelItems;
            try {
                JsonNode root = mapper.readTree(response.content());
                JsonNode array = root == null ? null : root.path("items");
                if (array == null || !array.isArray()) {
                    continue;
                }
                modelItems = new ArrayList<>();
                for (JsonNode node : array) {
                    modelItems.add(node);
                }
            } catch (Exception exception) {
                continue;
            }
            for (JsonNode node : modelItems) {
                String rawType = textOrNull(node, "requirementType");
                String description = textOrNull(node, "description");
                BigDecimal hours = parseHours(node.get("hours"));
                String catalogSpelling = (rawType == null) ? null : catalogByNorm.get(normalize(rawType));
                boolean valid = catalogSpelling != null
                        && description != null && !description.isBlank()
                        && hours != null && hours.compareTo(BigDecimal.ZERO) > 0
                        && hours.compareTo(new BigDecimal("2000")) <= 0
                        && containsHours(page, hours);
                if (valid) {
                    items.add(new Item(catalogSpelling, description.trim(), hours, pageNumber));
                } else {
                    rejected++;
                }
            }
        }
        return new Result(items, rejected);
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return (text == null) ? null : text;
    }

    private static BigDecimal parseHours(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            if (node.isNumber()) {
                return node.decimalValue();
            }
            // Real models sometimes answer "50 h" instead of 50: keep the leading number, grounding still applies.
            java.util.regex.Matcher number = Pattern.compile("^\\s*(\\d+(?:[.,]\\d+)?)").matcher(node.asText());
            return number.find() ? new BigDecimal(number.group(1).replace(',', '.')) : null;
        } catch (Exception exception) {
            return null;
        }
    }

    private static boolean containsHours(String page, BigDecimal hours) {
        String number = hours.stripTrailingZeros().toPlainString();
        String pattern = "(?<![0-9.,])" + Pattern.quote(number) + "\\s*(?:h|horas)\\b";
        return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(page).find();
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).trim();
    }
}