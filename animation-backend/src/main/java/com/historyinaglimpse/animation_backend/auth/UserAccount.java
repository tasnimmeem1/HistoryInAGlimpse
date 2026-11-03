package com.historyinaglimpse.animation_backend.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One row of public.app_users. The password hash never leaves the backend.
 * Accounts made with Google sign-in have a googleSub and may have no password.
 */
public record UserAccount(
        Long id,
        String username,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("password_hash") String passwordHash,
        @JsonProperty("google_sub") String googleSub,
        Role role) {

    public UserProfile profile() {
        return new UserProfile(id, username, displayName, role, passwordHash != null, googleSub != null);
    }

    /** What the desktop app receives about a user. */
    public record UserProfile(Long id, String username, String displayName, Role role,
                              boolean hasPassword, boolean google) { }
}
