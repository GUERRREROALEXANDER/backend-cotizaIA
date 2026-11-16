package com.cotizaia.ingest.gmail;

/** Verifies the OIDC token Pub/Sub attaches to every push request. */
public interface PushTokenVerifier {

    /** @return true only for a valid Google-signed token meant for this endpoint */
    boolean verify(String bearerToken);
}
