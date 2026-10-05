package com.cotizaia.api;

import com.cotizaia.domain.Role;

/** Public projection without persistence internals. */
public record RoleResponse(Long id, String name) {

    public static RoleResponse from(Role role) {
        return new RoleResponse(role.getId(), role.getName());
    }
}
