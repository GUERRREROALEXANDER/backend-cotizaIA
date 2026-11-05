package com.cotizaia.api;

import com.cotizaia.config.SecurityProperties;
import com.cotizaia.service.AuthService;
import com.cotizaia.service.AuthService.IssuedRefreshToken;
import com.cotizaia.service.JwtTokenService.IssuedToken;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes agency registration, credential login and the cookie session
 * lifecycle (project.txt section 2, spec 02). The controller only translates
 * HTTP input and output: credentials never appear in a JS-readable body, they
 * travel as HttpOnly cookies built here, and credential handling stays in
 * AuthService.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    static final String ACCESS_COOKIE = "cz_access";

    static final String ACCESS_COOKIE_PATH = "/api";

    static final String REFRESH_COOKIE = "cz_refresh";

    static final String REFRESH_COOKIE_PATH = "/api/auth";

    private final AuthService authService;

    private final SecurityProperties securityProperties;

    public AuthController(AuthService authService, SecurityProperties securityProperties) {
        this.authService = authService;
        this.securityProperties = securityProperties;
    }

    @PostMapping("/register")
    @Operation(summary = "Register an agency and owner")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return issued(authService.register(request.agencyName(), request.ownerFullName(),
                request.email(), request.password()), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate with credentials")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return issued(authService.login(request.email(), request.password()), HttpStatus.OK);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate the refresh cookie into a fresh session")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
        return issued(authService.refresh(refreshCookieValue(request)), HttpStatus.OK);
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the refresh-token family and clear the cookies")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(refreshCookieValue(request));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearedCookie(ACCESS_COOKIE, ACCESS_COOKIE_PATH).toString())
                .header(HttpHeaders.SET_COOKIE, clearedCookie(REFRESH_COOKIE, REFRESH_COOKIE_PATH).toString())
                .build();
    }

    @GetMapping("/csrf")
    @Operation(summary = "Issue the XSRF-TOKEN cookie for the SPA double submit")
    public ResponseEntity<Void> csrf(CsrfToken token) {
        // Spring Security 6 defers CSRF token rendering: touching the value
        // forces the XSRF-TOKEN cookie onto this response.
        token.getToken();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Get the authenticated identity")
    public CurrentUser me(CurrentUser user) {
        return user;
    }

    private ResponseEntity<AuthResponse> issued(AuthService.RegistrationResult result, HttpStatus status) {
        AuthResponse body = AuthResponse.from(result);
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, accessCookie(result.accessToken()).toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken()).toString())
                .body(body);
    }

    private ResponseEntity<AuthResponse> issued(AuthService.LoginResult result, HttpStatus status) {
        AuthResponse body = AuthResponse.from(result);
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, accessCookie(result.accessToken()).toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken()).toString())
                .body(body);
    }

    private ResponseCookie accessCookie(IssuedToken access) {
        return ResponseCookie.from(ACCESS_COOKIE, access.token())
                .httpOnly(true).secure(true).sameSite("Strict").path(ACCESS_COOKIE_PATH)
                .maxAge(securityProperties.ttl().toSeconds()).build();
    }

    private ResponseCookie refreshCookie(IssuedRefreshToken refresh) {
        return ResponseCookie.from(REFRESH_COOKIE, refresh.token())
                .httpOnly(true).secure(true).sameSite("Strict").path(REFRESH_COOKIE_PATH)
                .maxAge(securityProperties.refreshTtl().toSeconds()).build();
    }

    private ResponseCookie clearedCookie(String name, String path) {
        return ResponseCookie.from(name, "").httpOnly(true).secure(true).sameSite("Strict")
                .path(path).maxAge(0).build();
    }

    private String refreshCookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (REFRESH_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
