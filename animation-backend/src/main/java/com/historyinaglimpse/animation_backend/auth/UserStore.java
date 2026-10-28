package com.historyinaglimpse.animation_backend.auth;

import java.util.Optional;

/** Where accounts are kept. Supabase in the app, in memory in tests. */
public interface UserStore {
    Optional<UserAccount> findByUsername(String username);

    /** Saves a new account and returns it with its database id. */
    UserAccount create(String username, String displayName, String passwordHash, Role role);
}
