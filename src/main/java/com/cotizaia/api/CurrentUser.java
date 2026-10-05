package com.cotizaia.api;

/** Authenticated identity projected from verified JWT claims. */
public record CurrentUser(Long userId, Long agencyId, String role, String email) {
}
