package com.cotizaia.agent.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class EmbeddingPropertiesTests {

    private final EmbeddingProperties properties = new EmbeddingProperties();

    @Test
    void vectorsDefaultTo1024DimensionsInBatchesOf64() {
        assertThat(properties.getDimensions()).isEqualTo(1024);
        assertThat(properties.getBatchSize()).isEqualTo(64);
        assertThat(properties.getCacheSize()).isEqualTo(10_000);
    }

    @Test
    void voyageUsesTheLiteModelAndHasNoKeyByDefault() {
        assertThat(properties.getVoyage().getModel()).isEqualTo("voyage-4-lite");
        assertThat(properties.getVoyage().getBaseUrl()).isEqualTo("https://api.voyageai.com/v1");
        assertThat(properties.getVoyage().getApiKey()).isEmpty();
    }

    @Test
    void ollamaPointsToTheLocalServer() {
        assertThat(properties.getOllama().getBaseUrl()).isEqualTo("http://localhost:11434");
        assertThat(properties.getOllama().getModel()).isEqualTo("nomic-embed-text");
    }

    @Test
    void retriesBackOffFromFiveSecondsUpToOneMinute() {
        EmbeddingProperties.Retry retry = properties.getRetry();

        assertThat(retry.getMaxAttempts()).isEqualTo(5);
        assertThat(retry.getInitialBackoff()).isEqualTo(Duration.ofSeconds(5));
        assertThat(retry.getMultiplier()).isEqualTo(2.0);
        assertThat(retry.getMaxBackoff()).isEqualTo(Duration.ofSeconds(60));
    }
}
