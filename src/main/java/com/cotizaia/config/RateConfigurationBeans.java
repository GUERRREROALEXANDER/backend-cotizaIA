package com.cotizaia.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the {@link RateConfiguration} GoF Singleton as a Spring-managed bean.
 *
 * <p>The single instance is owned by {@link RateConfiguration#getInstance()}
 * (initialization-on-demand holder); this bean simply publishes that same
 * object so injection and direct lookups never diverge into two caches.
 */
@Configuration
public class RateConfigurationBeans {

    @Bean
    public RateConfiguration rateConfiguration() {
        return RateConfiguration.getInstance();
    }
}
