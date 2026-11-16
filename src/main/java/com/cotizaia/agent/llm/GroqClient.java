package com.cotizaia.agent.llm;

import org.springframework.web.client.RestClient;

/** Adapter that translates the agent's requests to Groq's OpenAI-compatible chat API. */
public class GroqClient extends OpenAiCompatibleClient {

    public GroqClient(RestClient.Builder builder, LlmProperties properties) {
        super(builder, "groq", "GROQ_API_KEY", properties.getGroq(), properties.getTimeout());
    }
}
