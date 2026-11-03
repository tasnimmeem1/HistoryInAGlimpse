package com.historyinaglimpse.animation_backend.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

/** One row of public.app_users. The password hash never leaves the backend. */
public record UserAccount(
        Long id,
        String username,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("password_hash") String passwordHash,
        Role role) {

    public UserProfile profile() {
        return new UserProfile(id, username, displayName, role);
    }

    /** What the desktop app receives about a user. */
    public record UserProfile(Long id, String username, String displayName, Role role) { }
}
