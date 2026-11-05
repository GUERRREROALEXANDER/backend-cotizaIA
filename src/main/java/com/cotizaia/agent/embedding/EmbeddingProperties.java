package com.cotizaia.agent.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds embedding provider settings without embedding deployment secrets in code. */
@ConfigurationProperties(prefix = "embedding")
public class EmbeddingProperties {

    private Voyage voyage = new Voyage();
    private Ollama ollama = new Ollama();
    private int dimensions = 1024;
    private int batchSize = 64;
    private int cacheSize = 10000;

    public Voyage getVoyage() {
        return voyage;
    }

    public void setVoyage(Voyage voyage) {
        this.voyage = voyage;
    }

    public Ollama getOllama() {
        return ollama;
    }

    public void setOllama(Ollama ollama) {
        this.ollama = ollama;
    }

    public int getDimensions() {
        return dimensions;
    }

    public void setDimensions(int dimensions) {
        this.dimensions = dimensions;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(int cacheSize) {
        this.cacheSize = cacheSize;
    }

    public static class Voyage {
        private String baseUrl = "https://api.voyageai.com/v1";
        private String apiKey = "";
        private String model = "voyage-4-lite";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    public static class Ollama {
        private String baseUrl = "http://localhost:11434";
        private String model = "nomic-embed-text";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }
}
