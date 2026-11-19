package com.cotizaia.service;

import com.cotizaia.config.SecurityProperties;
import com.cotizaia.domain.Agency;
import com.cotizaia.domain.AppUser;
import com.cotizaia.domain.RefreshToken;
import com.cotizaia.domain.UserRole;
import com.cotizaia.mail.EmailNotVerifiedException;
import com.cotizaia.mail.EmailVerificationService;
import com.cotizaia.mail.MailProperties;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.crypto.BlindIndex;
import com.cotizaia.repository.AppUserRepository;
import com.cotizaia.repository.RefreshTokenRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Registers the agency aggregate and authenticates its credentialed users
 * (project.txt section 2, spec 02). Password hashing stays in the application
 * layer so the domain never receives raw passwords. Login grants a short-lived
 * access token plus a rotating refresh token; presenting a rotated-away or
 * unknown refresh token fails, and reuse kills the whole session family.
 */
@Service
public class AuthService {

    private final AgencyRepository agencies;

    private final AppUserRepository users;

    private final RefreshTokenRepository refreshTokens;

    private final PasswordEncoder passwords;

    private final JwtTokenService tokens;

    private final SecurityProperties securityProperties;

    private final BlindIndex blindIndex;

    private final MailProperties mail;

    private final EmailVerificationService verification;

    private final String dummyHash;

    private final TransactionTemplate transactionTemplate;

    public AuthService(AgencyRepository agencies, AppUserRepository users,
            RefreshTokenRepository refreshTokens, PasswordEncoder passwords, JwtTokenService tokens,
            BlindIndex blindIndex, SecurityProperties securityProperties,
            MailProperties mail, EmailVerificationService verification,
            PlatformTransactionManager transactionManager) {
        this.agencies = agencies;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwords = passwords;
        this.tokens = tokens;
        this.blindIndex = blindIndex;
        this.securityProperties = securityProperties;
        this.mail = mail;
        this.verification = verification;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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
        // Spec 16: the flag only bites when a mailer exists to complete the flow.
        boolean verificationRequired = mail.isLoginVerificationRequired();
        Agency agency = new Agency(agencyName);
        AppUser owner = new AppUser(agency, ownerFullName, loginEmail, UserRole.OWNER);
        owner.setEmailVerified(!verificationRequired);
        owner.assignCredentials(loginEmail, passwords.encode(rawPassword));
        owner.setEmailHash(blindIndex.hash(owner.getEmail()));
        owner.setLoginEmailHash(blindIndex.hash(owner.getLoginEmail()));
        agency.addUser(owner);
        agencies.saveAndFlush(agency);
        if (mail.isEnabled()) {
            verification.sendVerification(owner);
        }
        return new RegistrationResult(agency.getId(), owner.getId(), owner.getRole().name(),
                verificationRequired, tokens.issue(owner), issueRefreshToken(owner));
    }

    @Transactional
    public LoginResult login(String email, String rawPassword) {
        AppUser user = users.findByLoginEmail(normalizeEmail(email)).orElse(null);
        String hash = user == null || !user.hasCredentials() ? dummyHash : user.getPasswordHash();
        boolean matches = passwords.matches(rawPassword == null ? "" : rawPassword, hash);
        if (user == null || !user.hasCredentials() || !matches) {
            throw new BadCredentialsException("Invalid email or password");
        }
        // Checked after the password so an unverified account is only revealed to its owner.
        if (mail.isLoginVerificationRequired() && !user.isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }
        return new LoginResult(user.getAgency().getId(), user.getId(), user.getRole().name(),
                tokens.issue(user), issueRefreshToken(user));
    }

    /** Redeems a verification link (spec 16); an unusable token surfaces as a 400, not a 500. */
    @Transactional
    public void verifyEmail(String token) {
        verification.verify(token);
    }

    /** Asks for another verification link; the answer is identical whether or not the account exists. */
    public void resendVerification(String email) {
        verification.resend(email);
    }

    /**
     * Rotates a presented refresh token: revokes it (replaced_by points at the
     * replacement), issues a new access token and a new refresh token in the
     * same family. Unknown, expired or already-revoked presentations fail; a
     * revoked presentation is replay evidence and revokes the whole family.
     */
    @Transactional
    public LoginResult refresh(String presentedToken) {
        if (presentedToken == null || presentedToken.isBlank()) {
            throw new BadCredentialsException("Refresh token required");
        }
        RefreshToken stored = refreshTokens.findByTokenHash(RefreshToken.hash(presentedToken))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        if (stored.isRevoked()) {
            revokeFamilyInNewTransaction(stored.getFamilyId());
            throw new BadCredentialsException("Invalid refresh token");
        }
        Instant now = Instant.now();
        if (stored.isExpired(now)) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        AppUser user = stored.getUser();
        JwtTokenService.IssuedToken access = tokens.issue(user);
        IssuedRefreshToken replacement = issueRefreshToken(user, stored.getFamilyId());
        stored.revoke(now, replacement.id());
        return new LoginResult(user.getAgency().getId(), user.getId(), user.getRole().name(),
                access, replacement);
    }

    /** Revokes the whole family of the presented refresh token, if it exists. */
    @Transactional
    public void logout(String presentedToken) {
        if (presentedToken == null || presentedToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(RefreshToken.hash(presentedToken))
                .ifPresent(stored -> revokeFamily(stored.getFamilyId()));
    }

    /** Display names for the signed-in user; empty when the token names a user that no longer exists. */
    @Transactional(readOnly = true)
    public Optional<Profile> profile(Long userId, Long agencyId) {
        return users.findById(userId)
                .filter(user -> user.getAgency().getId().equals(agencyId))
                .map(user -> new Profile(user.getFullName(), user.getAgency().getName()));
    }

    private IssuedRefreshToken issueRefreshToken(AppUser user) {
        return issueRefreshToken(user, UUID.randomUUID().toString());
    }

    private IssuedRefreshToken issueRefreshToken(AppUser user, String familyId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(securityProperties.refreshTtl());
        String rawToken = RefreshToken.newTokenValue();
        RefreshToken created = refreshTokens.saveAndFlush(
                new RefreshToken(user, familyId, rawToken, expiresAt, now));
        return new IssuedRefreshToken(created.getId(), rawToken, expiresAt);
    }

    private void revokeFamily(String familyId) {
        refreshTokens.revokeFamily(familyId, Instant.now());
    }

    /**
     * Reuse of a rotated token must kill the family even though the failing
     * refresh request itself rolls back, so the revocation commits in its own
     * transaction.
     */
    private void revokeFamilyInNewTransaction(String familyId) {
        transactionTemplate.executeWithoutResult(status -> revokeFamily(familyId));
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Registration identity and issued session tokens, without credential material.
     * {@code emailVerificationRequired} tells the client to show the "check your mailbox" screen:
     * it is true only when mail is enabled and the deployment enforces verification.
     */
    public record RegistrationResult(Long agencyId, Long userId, String role, boolean emailVerificationRequired,
            JwtTokenService.IssuedToken accessToken, IssuedRefreshToken refreshToken) {
    }

    /** Authenticated identity and issued session tokens, without credential material. */
    public record LoginResult(Long agencyId, Long userId, String role,
            JwtTokenService.IssuedToken accessToken, IssuedRefreshToken refreshToken) {
    }

    /**
     * A newly stored refresh token. {@code token} is the raw value for the
     * Set-Cookie header only; persistence keeps the hash (spec 02). {@code id}
     * links the old row's {@code replaced_by} to its replacement.
     */
    public record IssuedRefreshToken(Long id, String token, Instant expiresAt) {
    }

    public record Profile(String fullName, String agencyName) {
    }
}
