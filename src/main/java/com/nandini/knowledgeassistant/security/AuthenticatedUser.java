package com.nandini.knowledgeassistant.security;

import com.nandini.knowledgeassistant.user.Role;

import java.util.UUID;

/** The principal placed in the SecurityContext after a JWT has been verified. */
public record AuthenticatedUser(UUID id, String username, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
