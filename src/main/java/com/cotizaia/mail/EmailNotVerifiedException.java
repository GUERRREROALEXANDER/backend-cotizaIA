package com.cotizaia.mail;

/**
 * Login attempt by an account whose mailbox is still unproven (spec 16). Mapped to
 * {@code 403 {"error":"Email not verified"}} so the client can offer "resend" instead of
 * telling the user their password is wrong.
 */
public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException() {
        super("Email not verified");
    }
}
