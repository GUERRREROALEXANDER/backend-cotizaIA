package com.cotizaia.agent.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LlmRequestTests {

    @Test
    void aRequestNeedsATask() {
        assertThatThrownBy(() -> new LlmRequest(null, "system", "user", false))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("task");
    }

    @Test
    void bothPromptsMustHaveText() {
        assertThatThrownBy(() -> new LlmRequest(LlmTask.CLASSIFY, " ", "user", false))
                .hasMessageContaining("systemPrompt");
        assertThatThrownBy(() -> new LlmRequest(LlmTask.CLASSIFY, "system", null, false))
                .hasMessageContaining("userPrompt");
    }

    @Test
    void aValidRequestKeepsItsJsonFlag() {
        LlmRequest request = new LlmRequest(LlmTask.EXTRACT, "Eres un analista", "Brief: tienda", true);

        assertThat(request.jsonOutput()).isTrue();
        assertThat(request.task()).isEqualTo(LlmTask.EXTRACT);
    }

    @Test
    void theAgentHasTasksForEveryStepThatCallsAModel() {
        assertThat(LlmTask.values()).contains(LlmTask.CLASSIFY, LlmTask.EXTRACT, LlmTask.EXTRACT_ITEMS,
                LlmTask.DRAFT_PROPOSAL, LlmTask.CHAT);
    }
}
