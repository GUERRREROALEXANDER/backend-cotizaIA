package com.cotizaia.agent.llm;

/** Target interface that lets the agent use local or cloud models interchangeably. */
public interface LlmClient {

    LlmResponse complete(LlmRequest request);

    String provider();
}
