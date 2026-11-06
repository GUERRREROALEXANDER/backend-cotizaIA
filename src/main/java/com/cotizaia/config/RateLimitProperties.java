package com.cotizaia.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Fixed-window limits for the two abuse surfaces (spec 08): credential
 * endpoints are limited per client IP to slow brute force, and the endpoints
 * that spend LLM/embedding money are limited per agency. Limits are settings,
 * not constants, so production can tune them without a rebuild while tests
 * bind small values.
 */
@ConfigurationProperties("security.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;

    private Bucket auth = new Bucket(10, Duration.ofMinutes(15));

    private Bucket ai = new Bucket(30, Duration.ofMinutes(1));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Bucket getAuth() {
        return auth;
    }

    public void setAuth(Bucket auth) {
        this.auth = auth;
    }

    public Bucket getAi() {
        return ai;
    }

    public void setAi(Bucket ai) {
        this.ai = ai;
    }

    /** Capacity and window for one bucket; both are required to be positive. */
    public static class Bucket {

        private int capacity;

        private Duration window;

        public Bucket() {
        }

        public Bucket(int capacity, Duration window) {
            this.capacity = capacity;
            this.window = window;
        }

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }
    }
}
