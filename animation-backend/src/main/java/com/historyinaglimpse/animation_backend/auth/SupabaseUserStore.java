package com.historyinaglimpse.animation_backend.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Accounts in the Supabase table public.app_users (see sql/app_users.sql). */
@Repository
public class SupabaseUserStore implements UserStore {
    private static final ParameterizedTypeReference<List<UserAccount>> ROWS = new ParameterizedTypeReference<>() {};
    private static final Logger LOG = LoggerFactory.getLogger(SupabaseUserStore.class);
    private final RestClient client;

    public SupabaseUserStore(@Value("${supabase.url}") String url, @Value("${supabase.secret-key}") String key) {
        client = RestClient.builder().baseUrl(url).defaultHeader("apikey", key).build();
    }

    @Override public Optional<UserAccount> findByUsername(String username) {
        return findOne("username", username);
    }

    @Override public Optional<UserAccount> findByGoogleSub(String googleSub) {
        return findOne("google_sub", googleSub);
    }

    private Optional<UserAccount> findOne(String column, String value) {
        try {
            List<UserAccount> rows = client.get()
                    .uri("/rest/v1/app_users?select=*&" + column + "=eq."
                            + UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8))
                    .retrieve().body(ROWS);
            return rows == null ? Optional.empty() : rows.stream().findFirst();
        } catch (RestClientException ex) {
            throw failure(ex, "Could not reach the account database.");
        }
    }

    @Override public List<UserAccount> findAll() {
        try {
            List<UserAccount> rows = client.get().uri("/rest/v1/app_users?select=*&order=id.asc")
                    .retrieve().body(ROWS);
            return rows == null ? List.of() : rows;
        } catch (RestClientException ex) {
            throw failure(ex, "Could not reach the account database.");
        }
    }

    @Override public UserAccount update(String username, Map<String, String> columns) {
        try {
            List<UserAccount> rows = client.patch()
                    .uri("/rest/v1/app_users?username=eq."
                            + UriUtils.encodeQueryParam(username, StandardCharsets.UTF_8))
                    .header("Prefer", "return=representation")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(columns)
                    .retrieve().body(ROWS);
            if (rows == null || rows.isEmpty())
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No account with that username.");
            return rows.get(0);
        } catch (RestClientException ex) {
            throw failure(ex, "Could not save the account.");
        }
    }

    @Override public UserAccount create(String username, String displayName, String passwordHash,
                                       String googleSub, Role role) {
        Map<String, String> row = new HashMap<>();
        row.put("username", username);
        row.put("display_name", displayName);
        // Leave out empty columns, so password sign-up still works on a table
        // made before Google sign-in added google_sub.
        if (passwordHash != null) row.put("password_hash", passwordHash);
        if (googleSub != null) row.put("google_sub", googleSub);
        row.put("role", role.name());
        try {
            List<UserAccount> rows = client.post().uri("/rest/v1/app_users")
                    .header("Prefer", "return=representation")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(row)
                    .retrieve().body(ROWS);
            if (rows == null || rows.isEmpty())
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The account was not saved.");
            return rows.get(0);
        } catch (RestClientResponseException ex) {
            // 409 is a unique constraint: almost always the username.
            if (ex.getStatusCode().value() == 409)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is taken.");
            throw failure(ex, "Could not save the account.");
        } catch (RestClientException ex) {
            throw failure(ex, "Could not reach the account database.");
        }
    }

    /**
     * Turns a Supabase error into a message that says how to fix it, since it
     * is shown on the login form. Setup mistakes are the usual cause.
     */
    static ResponseStatusException failure(RestClientException ex, String fallback) {
        String message = fallback;
        if (ex instanceof RestClientResponseException response) {
            int status = response.getStatusCode().value();
            String body = response.getResponseBodyAsString();
            LOG.warn("Supabase answered {}: {}", status, body);
            if (status == 401 || status == 403)
                message = "Supabase refused the backend's key. Check SUPABASE_SECRET_KEY in the backend run configuration.";
            else if (body.contains("PGRST205") || body.contains("42P01"))
                message = "The account table is missing. Run sql/app_users.sql in the Supabase SQL editor.";
            else if (body.contains("PGRST204") || body.contains("42703"))
                message = "The account table is out of date. Run sql/app_users.sql again in the Supabase SQL editor.";
        } else {
            LOG.warn("Could not reach Supabase: {}", ex.getMessage());
        }
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, message);
    }
}
