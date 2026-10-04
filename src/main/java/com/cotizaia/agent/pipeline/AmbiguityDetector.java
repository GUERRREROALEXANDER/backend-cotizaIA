package com.cotizaia.agent.pipeline;

import com.cotizaia.domain.ExtractedRequirement;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Finds deterministic scope gaps (project.txt section 2) so reviewers receive concrete questions.
 * Attachment checks are only pre-analysis signals; they do not certify document readability or content.
 */
@Component
public class AmbiguityDetector {

    private static final String PAGE = "(?:paginas?|seccion(?:es)?|pantallas?)";

    private static final String NUMBER = "(?:\\d+|una? sol[ao]|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez)";

    private static final Pattern PAGE_COUNT = Pattern.compile(
            "\\b(?:" + NUMBER + "(?:\\s+\\p{L}+){0,2}\\s+" + PAGE
                    + "|" + PAGE + "(?:\\s+\\p{L}+){0,2}\\s*[:=]?\\s+" + NUMBER + ")\\b");

    private static final Pattern DEADLINE = Pattern.compile(
            "\\b(?:semanas?|mes(?:es)?|dias?|fecha|plazo|urgente|deadline|entrega para|antes de)\\b");

    private final ObjectMapper mapper;

    public AmbiguityDetector(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public List<AmbiguityFinding> detect(PipelineContext context) {
        String text = normalize(context.analysisText());
        List<AmbiguityFinding> findings = new ArrayList<>();
        if (("WEB_SITE".equals(context.getProjectType()) || "ECOMMERCE".equals(context.getProjectType()))
                && !PAGE_COUNT.matcher(text).find()) {
            findings.add(new AmbiguityFinding("PAGE_COUNT_MISSING",
                    "\u00bfCuantas paginas o secciones necesitas?", false));
        }
        if (!DEADLINE.matcher(text).find()) {
            findings.add(new AmbiguityFinding("DEADLINE_MISSING", "\u00bfPara cuando necesitas la entrega?", false));
        }
        for (int index = 0; index < context.getRequirements().size(); index++) {
            RequirementDraft draft = context.getRequirements().get(index);
            if (draft.getConfidence().compareTo(ExtractedRequirement.LOW_CONFIDENCE_THRESHOLD) < 0) {
                findings.add(new AmbiguityFinding("LOW_CONFIDENCE_" + index,
                        "\u00bfPuedes confirmar el requisito " + draft.getDescription() + "?", false));
            }
        }
        if ((text.contains("sin pagos") && text.contains("pasarela de pago"))
                || (text.contains("sin registro") && (text.contains("login")
                        || text.contains("inicio de sesion")))
                || ((text.contains("una sola pagina") || text.contains("landing"))
                        && text.contains("varias paginas"))) {
            findings.add(new AmbiguityFinding("CONTRADICTION",
                    "\u00bfPuedes aclarar los requisitos contradictorios?", false));
        }
        if (hasUnreadableAttachment(context.getBrief().getRawPayload())) {
            findings.add(new AmbiguityFinding("UNREADABLE_ATTACHMENT",
                    "\u00bfPuedes enviar el contenido del archivo en texto legible?", false));
        }
        Set<String> resolved = context.resolvedCodes();
        return findings.stream().filter(finding -> !resolved.contains(finding.code())).toList();
    }

    private boolean hasUnreadableAttachment(String payload) {
        if (payload == null || payload.isBlank()) {
            return false;
        }
        try {
            JsonNode root = mapper.readTree(payload);
            if (root == null || !root.path("attachments").isArray()) {
                return false;
            }
            for (JsonNode attachment : root.path("attachments")) {
                String text = attachment.path("text").asText("");
                long characters = text.codePoints().filter(value -> !Character.isWhitespace(value)).count();
                long letters = text.codePoints().filter(Character::isLetter).count();
                if (text.isBlank() || letters * 2 < characters) {
                    return true;
                }
            }
        } catch (JsonProcessingException ignored) {
            // Some channels keep a non-JSON payload; no attachment claim can be made from it.
            return false;
        }
        return false;
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
