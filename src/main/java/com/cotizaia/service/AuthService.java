package com.cotizaia.service;

import com.cotizaia.domain.Agency;
import com.cotizaia.domain.AppUser;
import com.cotizaia.domain.UserRole;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.AppUserRepository;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers the agency aggregate and authenticates its credentialed users (project.txt section 2).
 * Password hashing stays in the application layer so the domain never receives raw passwords.
 */
@Service
public class AuthService {

    private final AgencyRepository agencies;

    private final AppUserRepository users;

    private final PasswordEncoder passwords;

    private final JwtTokenService tokens;

    private final String dummyHash;

    public AuthService(AgencyRepository agencies, AppUserRepository users,
            PasswordEncoder passwords, JwtTokenService tokens) {
        this.agencies = agencies;
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public RegistrationResult register(String agencyName, String ownerFullName, String email, String rawPassword) {
        String loginEmail = normalizeEmail(email);
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new IllegalArgumentException("Password must contain at least 8 characters");
        }
        if (users.existsByLoginEmail(loginEmail)) {
            throw new IllegalStateException("Email is already registered");
        }
        Agency agency = new Agency(agencyName);
        AppUser owner = new AppUser(agency, ownerFullName, loginEmail, UserRole.OWNER);
        owner.assignCredentials(loginEmail, passwords.encode(rawPassword));
        agency.addUser(owner);
        agencies.saveAndFlush(agency);
        return new RegistrationResult(agency.getId(), owner.getId(), owner.getRole().name(), tokens.issue(owner));
    }

    @Transactional(readOnly = true)
    public LoginResult login(String email, String rawPassword) {
        AppUser user = users.findByLoginEmail(normalizeEmail(email)).orElse(null);
        String hash = user == null || !user.hasCredentials() ? dummyHash : user.getPasswordHash();
        boolean matches = passwords.matches(rawPassword == null ? "" : rawPassword, hash);
        if (user == null || !user.hasCredentials() || !matches) {
            throw new BadCredentialsException("Invalid email or password");
        }
        return new LoginResult(user.getAgency().getId(), user.getId(), user.getRole().name(), tokens.issue(user));
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** Registration identity and token, without credential material. */
    public record RegistrationResult(Long agencyId, Long userId, String role, JwtTokenService.IssuedToken issuedToken) {
    }

    /** Authenticated identity and token, without credential material. */
    public record LoginResult(Long agencyId, Long userId, String role, JwtTokenService.IssuedToken issuedToken) {
    }
}
