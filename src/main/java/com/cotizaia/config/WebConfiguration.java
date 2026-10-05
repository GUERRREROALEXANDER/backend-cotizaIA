package com.cotizaia.config;

import com.cotizaia.api.CurrentUserArgumentResolver;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the authenticated-user argument contract for MVC (project.txt section 2).
 * Explicit registration ensures controllers receive the caller's verified agency identity.
 */
@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    private final CurrentUserArgumentResolver currentUserResolver;

    public WebConfiguration(CurrentUserArgumentResolver currentUserResolver) {
        this.currentUserResolver = currentUserResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserResolver);
    }
}
