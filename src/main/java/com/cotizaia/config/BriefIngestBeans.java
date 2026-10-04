package com.cotizaia.config;

import com.cotizaia.ingest.BriefSource;
import com.cotizaia.ingest.BriefSourceRegistry;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link BriefSourceRegistry} from every {@link BriefSource} bean.
 *
 * <p>Why a bean and not a component with constructor injection: keeping the
 * registry construction here makes the discovery rule explicit and lets unit
 * tests build a registry from an arbitrary adapter list (including a test
 * double) without a Spring context.
 */
@Configuration
public class BriefIngestBeans {

    @Bean
    public BriefSourceRegistry briefSourceRegistry(List<BriefSource> briefSources) {
        return new BriefSourceRegistry(briefSources);
    }
}
