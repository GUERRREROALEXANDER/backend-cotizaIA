package com.cotizaia.agent.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Provides deterministic task responses for offline demos and tests without opening a network connection. */
public class StubLlmClient implements LlmClient {

    private static final Map<String, List<String>> TYPES = Map.of(
            "ECOMMERCE", List.of("tienda", "ecommerce", "e-commerce", "carrito", "vender en linea"),
            "MOBILE_APP", List.of("app", "aplicacion movil", "android", "ios"),
            "BRANDING", List.of("logo", "marca", "branding", "identidad"),
            "MARKETING", List.of("redes sociales", "marketing", "campana", "publicidad"),
            "WEB_SITE", List.of("pagina", "web", "sitio", "landing"));
    private static final List<String> PRIORITY = List.of(
            "ECOMMERCE", "MOBILE_APP", "BRANDING", "MARKETING", "WEB_SITE");

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public LlmResponse complete(LlmRequest request) {
        String brief = PromptBuilder.section(request.userPrompt(), "brief").orElse("").trim();
        String content = switch (request.task()) {
            case CLASSIFY -> classify(brief);
            case EXTRACT -> extract(brief, request.userPrompt());
            case SUMMARIZE -> "Resumen ejecutivo: " + brief;
        };
        return new LlmResponse(content, provider(), "offline-stub", 0);
    }

    @Override
    public String provider() {
        return "stub";
    }

    private String classify(String brief) {
        String normalized = normalize(brief);
        for (String type : PRIORITY) {
            if (TYPES.get(type).stream().anyMatch(keyword -> matches(normalized, keyword))) {
                return json(Map.of("projectType", type, "confidence", 0.90));
            }
        }
        return json(Map.of("projectType", "UNKNOWN", "confidence", 0.20));
    }

    private String extract(String brief, String prompt) {
        String normalized = normalize(brief);
        List<Map<String, Object>> requirements = new ArrayList<>();
        String catalog = PromptBuilder.section(prompt, "catalog").orElse("");
        for (String line : catalog.split("\\R")) {
            String[] entry = line.split("\\|", 2);
            if (entry.length != 2) {
                continue;
            }
            for (String keyword : entry[1].split(",")) {
                if (matches(normalized, keyword.trim())) {
                    requirements.add(Map.of("requirementType", entry[0].trim(),
                            "description", entry[0].trim(), "confidence", 0.9));
                    break;
                }
            }
        }
        return json(Map.of("requirements", requirements));
    }

    private boolean matches(String text, String keyword) {
        String word = normalize(keyword.trim());
        if (word.isBlank()) {
            return false;
        }
        if (word.length() >= 4) {
            return text.contains(word);
        }
        return java.util.regex.Pattern.compile("(?<![\\p{L}\\p{N}])" + java.util.regex.Pattern.quote(word)
                + "(?![\\p{L}\\p{N}])").matcher(text).find();
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to encode stub response", exception);
        }
    }
}
