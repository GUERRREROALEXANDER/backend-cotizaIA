package com.cotizaia.api;

import com.cotizaia.service.AuthService.Profile;

/** The authenticated identity plus display names (null when the user no longer exists). */
public record MeResponse(Long userId, Long agencyId, String role, String email, String fullName,
        String agencyName) {

    public static MeResponse from(CurrentUser user, Profile profile) {
        return new MeResponse(user.userId(), user.agencyId(), user.role(), user.email(),
                profile == null ? null : profile.fullName(), profile == null ? null : profile.agencyName());
    }
}
