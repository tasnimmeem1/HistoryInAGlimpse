package com.historyinaglimpse.animation_backend.auth;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Where accounts are kept. Supabase in the app, in memory in tests. */
public interface UserStore {
    Optional<UserAccount> findByUsername(String username);

    /** Saves a new account and returns it with its database id. */
    UserAccount create(String username, String displayName, String passwordHash, Role role);

    /** Every account, oldest first. */
    List<UserAccount> findAll();

    /**
     * Changes columns of one account and returns it. Keys are column names:
     * display_name, password_hash or role.
     */
    UserAccount update(String username, Map<String, String> columns);
}
