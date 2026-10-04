package com.cotizaia.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link RateConfiguration} Singleton as a Spring-managed bean.
 *
 * <p>Why a bean and not a static holder: the Singleton pattern is about a
 * single shared instance, and Spring's default singleton scope already
 * guarantees exactly that per application context. Making it a bean keeps the
 * class unit-testable (no static state leaking between tests) while preserving
 * the one-instance-per-application guarantee.
 */
@Configuration
public class RateConfigurationBeans {

    @Bean
    public RateConfiguration rateConfiguration() {
        return new RateConfiguration();
    }
}
