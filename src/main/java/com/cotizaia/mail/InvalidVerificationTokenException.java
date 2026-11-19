package com.cotizaia.mail;

/**
 * Unknown, expired or already redeemed verification link (spec 16). The client learns only that
 * the token is unusable, never which of the three cases applies.
 */
public class InvalidVerificationTokenException extends RuntimeException {

    public InvalidVerificationTokenException() {
        super("Invalid or expired token");
    }
}
