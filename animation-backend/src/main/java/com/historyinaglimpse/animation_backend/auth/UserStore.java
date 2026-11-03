package com.historyinaglimpse.animation_backend.auth;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Where accounts are kept. Supabase in the app, in memory in tests. */
public interface UserStore {
    Optional<UserAccount> findByUsername(String username);

    /** The account linked to a Google user id (the "sub" in Google's ID token). */
    Optional<UserAccount> findByGoogleSub(String googleSub);

    /**
     * Saves a new account and returns it with its database id. Either the
     * password hash or the Google id may be null, but not both.
     */
    UserAccount create(String username, String displayName, String passwordHash, String googleSub, Role role);

    /** Every account, oldest first. */
    List<UserAccount> findAll();

    /**
     * Changes columns of one account and returns it. Keys are column names:
     * display_name, password_hash or role.
     */
    UserAccount update(String username, Map<String, String> columns);
}
