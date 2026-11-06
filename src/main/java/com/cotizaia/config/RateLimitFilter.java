package com.cotizaia.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Stops brute force and LLM cost abuse before the request reaches a controller
 * (spec 08). Credential endpoints share a per-IP bucket; the endpoints that
 * call the model or embeddings share a per-agency bucket, so one tenant cannot
 * exhaust another tenant's budget.
 *
 * <p>Created inside the security chain (not as a {@code @Bean}/{@code @Component}
 * filter) for the same reason as {@code AccessCookieAuthenticationFilter}:
 * Spring Boot would otherwise register it in the servlet container too, where
 * it would run a second time outside the chain and outside authentication. It
 * sits after the bearer/cookie authentication filter so the agency claim is
 * available.</p>
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String JSON_TOO_MANY_REQUESTS = "{\"error\":\"Too many requests\"}";

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private static final Set<String> AUTH_PATHS =
            Set.of("/api/auth/login", "/api/auth/register", "/api/auth/refresh");

    private static final List<Rule> AI_RULES = List.of(
            new Rule(HttpMethod.POST, "/api/quotations"),
            new Rule(HttpMethod.POST, "/api/briefs/upload"),
            new Rule(HttpMethod.POST, "/api/briefs/*/process"),
            new Rule(HttpMethod.POST, "/api/briefs/*/questions/*/answer"),
            new Rule(HttpMethod.POST, "/api/knowledge/documents"),
            new Rule(HttpMethod.GET, "/api/knowledge/search"),
            new Rule(HttpMethod.POST, "/api/proposals/*/revise"));

    private final RateLimitProperties properties;

    private final RateLimiter limiter;

    public RateLimitFilter(RateLimitProperties properties, RateLimiter limiter) {
        this.properties = properties;
        this.limiter = limiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (!properties.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        RateLimiter.Decision decision;
        String path = request.getRequestURI();
        if (HttpMethod.POST.matches(request.getMethod()) && AUTH_PATHS.contains(path)) {
            RateLimitProperties.Bucket auth = properties.getAuth();
            decision = limiter.tryAcquire("ip:" + request.getRemoteAddr(), auth.getCapacity(), auth.getWindow());
        } else if (isAiEndpoint(request, path)) {
            String agency = currentAgencyId();
            if (agency == null) {
                // No verified identity: let the authorization layer answer 401.
                chain.doFilter(request, response);
                return;
            }
            RateLimitProperties.Bucket ai = properties.getAi();
            decision = limiter.tryAcquire("agency:" + agency, ai.getCapacity(), ai.getWindow());
        } else {
            chain.doFilter(request, response);
            return;
        }

        if (!decision.allowed()) {
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(JSON_TOO_MANY_REQUESTS);
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isAiEndpoint(HttpServletRequest request, String path) {
        for (Rule rule : AI_RULES) {
            if (rule.method.matches(request.getMethod()) && MATCHER.match(rule.pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private static String currentAgencyId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            Object agencyId = jwtAuthentication.getToken().getClaim("agencyId");
            if (agencyId != null && !agencyId.toString().isBlank()) {
                return agencyId.toString();
            }
        }
        return null;
    }

    private record Rule(HttpMethod method, String pattern) {
    }
}
