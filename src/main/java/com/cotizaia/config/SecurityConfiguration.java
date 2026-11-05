package com.cotizaia.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Cookie-session boundary for agency data (project.txt section 2, spec 02).
 * The access JWT travels in the Authorization header for API clients or in the
 * HttpOnly cz_access cookie for the SPA; the cookie is presented to the
 * resource server as a synthesized Authorization header just before
 * authentication, so CSRF semantics stay "browser-forged requests need a CSRF
 * token, header API clients do not". Refresh and logout authenticate with the
 * refresh cookie through permitAll endpoints, not the access token.
 */
@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityConfiguration.class);

    private static final String ACCESS_COOKIE = "cz_access";

    private static final String REFRESH_COOKIE = "cz_refresh";

    private static final Set<String> STATE_CHANGING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private static final Set<String> CSRF_EXEMPT_PATHS = Set.of("/api/auth/register", "/api/auth/login");

    @Bean
    public SecretKey jwtSecretKey(SecurityProperties properties) {
        byte[] bytes;
        if (properties.secret() == null || properties.secret().isBlank()) {
            bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            LOGGER.warn("JWT_SECRET is blank: generated a development-only key; tokens will not survive a restart");
        } else {
            bytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        }
        if (bytes.length < 32) {
            throw new IllegalArgumentException("security.jwt.secret (JWT_SECRET) must contain at least 32 UTF-8 bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey key, SecurityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CSRF applies only to state-changing requests that carry a session cookie
     * (cz_access or cz_refresh): the browser attaches those by itself, so they
     * are the forgeable ones. Requests carrying an Authorization header are
     * additionally excluded by CsrfConfigurer's own bearer/header exemption;
     * the token-issuing register and login endpoints are exempt here.
     */
    @Bean
    public RequestMatcher csrfProtectionMatcher() {
        return request -> !CSRF_EXEMPT_PATHS.contains(request.getRequestURI())
                && STATE_CHANGING_METHODS.contains(request.getMethod())
                && carriesSessionCookie(request);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, RequestMatcher csrfProtectionMatcher)
            throws Exception {
        AuthenticationEntryPoint entryPoint = (request, response, exception) -> {
            response.setStatus(401);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"Authentication required\"}");
        };
        AccessDeniedHandler deniedHandler = (request, response, exception) -> {
            response.setStatus(403);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"Access denied\"}");
        };
        return http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        // Plain token in the header (no XOR masking) so SPA
                        // double submit with X-XSRF-TOKEN works as-is.
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .requireCsrfProtectionMatcher(csrfProtectionMatcher))
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/refresh",
                                "/api/auth/logout", "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .oauth2ResourceServer(resource -> resource
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                // The BearerTokenResolver stays header-only so that the
                // CsrfConfigurer bearer exemption keeps covering genuine
                // Authorization-header clients while cookie requests stay
                // CSRF-checked; the cookie enters the resource server here.
                // Created here, not as a @Bean: Spring Boot registers every Filter bean in the servlet
                // container too, which would run it a second time outside the security chain.
                .addFilterBefore(new AccessCookieAuthenticationFilter(), BearerTokenAuthenticationFilter.class)
                .build();
    }

    private static boolean carriesSessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie cookie : cookies) {
            String name = cookie.getName();
            if (ACCESS_COOKIE.equals(name) || REFRESH_COOKIE.equals(name)) {
                return true;
            }
        }
        return false;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String allowedOrigins) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN"));
        cors.setExposedHeaders(List.of("Content-Disposition"));
        cors.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    /**
     * Moves the access JWT from the cz_access cookie into the Authorization
     * header when the request carries no header of its own, so API clients can
     * keep using the header while the SPA authenticates with the cookie. The
     * CSRF filter has already made its decision by the time this runs.
     */
    static class AccessCookieAuthenticationFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain chain) throws ServletException, IOException {
            if (request.getHeader("Authorization") != null || request.getCookies() == null) {
                chain.doFilter(request, response);
                return;
            }
            for (Cookie cookie : request.getCookies()) {
                if (ACCESS_COOKIE.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                    String tokenValue = cookie.getValue();
                    chain.doFilter(new HttpServletRequestWrapper(request) {
                        @Override
                        public String getHeader(String name) {
                            if ("Authorization".equalsIgnoreCase(name)) {
                                return "Bearer " + tokenValue;
                            }
                            return super.getHeader(name);
                        }

                        @Override
                        public Enumeration<String> getHeaders(String name) {
                            if ("Authorization".equalsIgnoreCase(name)) {
                                return Collections.enumeration(List.of("Bearer " + tokenValue));
                            }
                            return super.getHeaders(name);
                        }
                    }, response);
                    return;
                }
            }
            chain.doFilter(request, response);
        }
    }
}
