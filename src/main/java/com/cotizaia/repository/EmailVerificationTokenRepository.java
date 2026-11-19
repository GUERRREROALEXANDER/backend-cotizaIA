package com.cotizaia.repository;

import com.cotizaia.domain.EmailVerificationToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Verification links by hash (spec 16). The raw token is never a column, so lookups are by its SHA-256. */
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    /**
     * Claims one token atomically: the row flips to used only while it is still unused and inside
     * its window, so two concurrent redemptions of the same link cannot both succeed (returns 0
     * for the loser, the expired token and the already used one).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update EmailVerificationToken token set token.usedAt = :now "
            + "where token.id = :id and token.usedAt is null and token.expiresAt > :now")
    int consume(@Param("id") Long id, @Param("now") Instant now);
}
