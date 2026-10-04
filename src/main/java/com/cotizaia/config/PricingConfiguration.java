package com.cotizaia.config;

import com.cotizaia.pricing.PricingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers pricing margin properties for concrete strategies (project.txt section 6).
 * Keeping registration in configuration leaves the pricing calculations free of wiring concerns.
 */
@Configuration
@EnableConfigurationProperties(PricingProperties.class)
public class PricingConfiguration {
}
