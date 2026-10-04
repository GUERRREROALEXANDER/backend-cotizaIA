package com.cotizaia.agent.llm;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Owns prompt wording so vendor adapters and the offline model share one task contract. */
@Component
public class PromptBuilder {

    private static final String SYSTEM = "Follow the requested output schema. Never invent prices; pricing is "
            + "determined by rules, not AI.";

    public LlmRequest classify(String briefText, List<String> projectTypes) {
        String prompt = tag("brief", briefText) + tag("project_types", String.join(", ", projectTypes))
                + "Return only JSON: {\"projectType\":\"catalog name or UNKNOWN\",\"confidence\":0.0}.";
        return new LlmRequest(LlmTask.CLASSIFY, SYSTEM, prompt, true);
    }

    public LlmRequest extract(String briefText, List<CatalogPromptEntry> catalog) {
        String entries = catalog.stream().map(entry -> entry.name() + " | " + entry.keywords())
                .reduce((left, right) -> left + "\n" + right).orElse("");
        String prompt = tag("brief", briefText) + tag("catalog", entries)
                + "Return only JSON: {\"requirements\":[{\"requirementType\":\"catalog name\","
                + "\"description\":\"short description\",\"confidence\":0.0}]}\n";
        return new LlmRequest(LlmTask.EXTRACT, SYSTEM, prompt, true);
    }

    public LlmRequest summarize(String briefText, String projectType, List<String> requirementNames) {
        String prompt = tag("brief", briefText) + tag("project_type", projectType)
                + tag("requirements", String.join(", ", requirementNames))
                + "Write a short Spanish executive summary in plain text.";
        return new LlmRequest(LlmTask.SUMMARIZE, SYSTEM, prompt, false);
    }

    public static Optional<String> section(String prompt, String tag) {
        String startTag = "<" + tag + ">";
        String endTag = "</" + tag + ">";
        int start = prompt.indexOf(startTag);
        if (start < 0) {
            return Optional.empty();
        }
        int end = prompt.indexOf(endTag, start + startTag.length());
        return end < 0 ? Optional.empty() : Optional.of(prompt.substring(start + startTag.length(), end));
    }

    private String tag(String name, String value) {
        return "<" + name + ">\n" + value + "\n</" + name + ">\n";
    }

}
