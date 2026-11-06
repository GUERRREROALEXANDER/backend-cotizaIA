package com.cotizaia.agent.llm;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds provider, timeout and retry settings without embedding deployment secrets in code. */
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    private String provider = "ollama";
    private Ollama ollama = new Ollama();
    private Groq groq = new Groq();
    private Timeout timeout = new Timeout();
    private Retry retry = new Retry();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Ollama getOllama() {
        return ollama;
    }

    public void setOllama(Ollama ollama) {
        this.ollama = ollama;
    }

    public Groq getGroq() {
        return groq;
    }

    public void setGroq(Groq groq) {
        this.groq = groq;
    }

    public Timeout getTimeout() {
        return timeout;
    }

    public void setTimeout(Timeout timeout) {
        this.timeout = timeout;
    }

    public Retry getRetry() {
        return retry;
    }

    public void setRetry(Retry retry) {
        this.retry = retry;
    }

    public static class Ollama {
        private String baseUrl = "http://localhost:11434";
        private String model = "llama3.1";
        /** Blank for the local daemon; set for Ollama Cloud, which authenticates with a bearer token. */
        private String apiKey = "";

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

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey == null ? "" : apiKey.trim();
        }
    }

    public static class Groq {
        private String baseUrl = "https://api.groq.com/openai/v1";
        private String apiKey = "";
        private String model = "openai/gpt-oss-20b";
        /** gpt-oss reasoning effort (low|medium|high); blank = provider default. Hidden reasoning tokens count. */
        private String reasoningEffort = "";

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
    
        public String getReasoningEffort() {
            return reasoningEffort;
        }

        public void setReasoningEffort(String reasoningEffort) {
            this.reasoningEffort = reasoningEffort == null ? "" : reasoningEffort.trim();
        }
}

    public static class Timeout {
        private Duration connect = Duration.ofSeconds(5);
        private Duration read = Duration.ofSeconds(60);

        public Duration getConnect() {
            return connect;
        }

        public void setConnect(Duration connect) {
            this.connect = connect;
        }

        public Duration getRead() {
            return read;
        }

        public void setRead(Duration read) {
            this.read = read;
        }
    }

    public static class Retry {
        private int maxAttempts = 3;
        private Duration initialBackoff = Duration.ofMillis(500);
        private double multiplier = 2.0;
        private Duration maxBackoff = Duration.ofSeconds(60);

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getInitialBackoff() {
            return initialBackoff;
        }

        public void setInitialBackoff(Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
        }

        public double getMultiplier() {
            return multiplier;
        }

        public void setMultiplier(double multiplier) {
            this.multiplier = multiplier;
        }

        public Duration getMaxBackoff() {
            return maxBackoff;
        }

        public void setMaxBackoff(Duration maxBackoff) {
            this.maxBackoff = maxBackoff;
        }
    }
}
