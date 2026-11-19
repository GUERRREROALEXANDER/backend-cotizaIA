package com.cotizaia.mail;

import com.cotizaia.domain.AppUser;
import com.cotizaia.domain.EmailVerificationToken;
import com.cotizaia.repository.AppUserRepository;
import com.cotizaia.repository.EmailVerificationTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues, mails and redeems the account-verification links of spec 16.
 *
 * <p>Rules the class owns: the token is 32 {@link SecureRandom} bytes in URL-safe Base64, only its
 * SHA-256 is persisted, a link lives 24 hours and dies on first use. Mail failures are logged and
 * swallowed here: a broken SMTP server must never turn a registration into a 500, and the user
 * can always ask for another link.</p>
 */
@Service
public class EmailVerificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailVerificationService.class);

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Contract constant: the link is valid for 24 hours. */
    static final Duration TOKEN_TTL = Duration.ofHours(24);

    static final String SUBJECT = "Confirma tu correo en CotizaIA";

    private final EmailVerificationTokenRepository tokens;

    private final AppUserRepository users;

    private final EmailGateway gateway;

    private final MailProperties mail;

    public EmailVerificationService(EmailVerificationTokenRepository tokens, AppUserRepository users,
            EmailGateway gateway, MailProperties mail) {
        this.tokens = tokens;
        this.users = users;
        this.gateway = gateway;
        this.mail = mail;
    }

    /**
     * Creates a fresh single-use token for the user and mails the Spanish message with the link
     * {@code <APP_PUBLIC_URL>/verify-email?token=...}. Previous links stay valid until they expire;
     * that is harmless because redeeming any of them only marks the same mailbox verified.
     */
    @Transactional
    public void sendVerification(AppUser user) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }
        String rawToken = newToken();
        tokens.saveAndFlush(new EmailVerificationToken(user, sha256(rawToken),
                Instant.now().plus(TOKEN_TTL)));
        String link = linkBase() + "/verify-email?token=" + rawToken;
        EmailMessage message = new EmailMessage(user.getEmail(), SUBJECT, text(user, link));
        try {
            gateway.send(message);
        } catch (RuntimeException exception) {
            // Never fail the caller (registration, resend) because the mail server is down.
            LOGGER.warn("verification email could not be sent for user {}", user.getId());
        }
    }

    /**
     * Redeems a link: marks the account verified and burns the token. Unknown, expired and already
     * used tokens are indistinguishable to the caller.
     */
    @Transactional
    public void verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidVerificationTokenException();
        }
        EmailVerificationToken token = tokens.findByTokenHash(sha256(rawToken.trim()))
                .orElseThrow(InvalidVerificationTokenException::new);
        Long userId = token.getUser().getId();
        // The claim clears the persistence context, so the user is reloaded afterwards.
        if (tokens.consume(token.getId(), Instant.now()) == 0) {
            throw new InvalidVerificationTokenException();
        }
        users.findById(userId).ifPresent(AppUser::markEmailVerified);
    }

    /**
     * Sends a new link only when the account exists and is still unverified. The endpoint answers
     * 204 either way, so the response cannot be used to enumerate registered addresses.
     */
    @Transactional
    public void resend(String email) {
        if (!mail.isEnabled() || email == null || email.isBlank()) {
            return;
        }
        users.findByLoginEmail(email.trim().toLowerCase(Locale.ROOT))
                .filter(user -> !user.isEmailVerified())
                .ifPresent(this::sendVerification);
    }

    /** Demo seeding helper: the seeded owner is trusted, so the flag never locks the demo out. */
    @Transactional
    public void markVerified(String loginEmail) {
        if (loginEmail == null || loginEmail.isBlank()) {
            return;
        }
        users.findByLoginEmail(loginEmail.trim().toLowerCase(Locale.ROOT))
                .ifPresent(AppUser::markEmailVerified);
    }

    private String linkBase() {
        String base = mail.getPublicUrl() == null ? "" : mail.getPublicUrl().trim();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private static String text(AppUser user, String link) {
        String name = user.getFullName() == null || user.getFullName().isBlank()
                ? "" : " " + user.getFullName();
        return "Hola" + name + ":\n\n"
                + "Gracias por crear tu cuenta en CotizaIA. Para confirmar tu correo abre este enlace:\n\n"
                + link + "\n\n"
                + "El enlace vence en 24 horas y solo se puede usar una vez. "
                + "Si no creaste esta cuenta, ignora este mensaje.";
    }

    /** 32 random bytes, URL-safe Base64 without padding so the value survives a query string. */
    static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** The stored representation: hex SHA-256, never the token itself. */
    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
