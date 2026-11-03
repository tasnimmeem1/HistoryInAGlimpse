package com.historyinaglimpse.animation_backend.auth;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Accounts in the Supabase table public.app_users (see sql/app_users.sql). */
@Repository
public class SupabaseUserStore implements UserStore {
    private static final ParameterizedTypeReference<List<UserAccount>> ROWS = new ParameterizedTypeReference<>() {};
    private final RestClient client;

    public SupabaseUserStore(@Value("${supabase.url}") String url, @Value("${supabase.secret-key}") String key) {
        client = RestClient.builder().baseUrl(url).defaultHeader("apikey", key).build();
    }

    @Override public Optional<UserAccount> findByUsername(String username) {
        try {
            List<UserAccount> rows = client.get()
                    .uri("/rest/v1/app_users?select=*&username=eq."
                            + UriUtils.encodeQueryParam(username, StandardCharsets.UTF_8))
                    .retrieve().body(ROWS);
            return rows == null ? Optional.empty() : rows.stream().findFirst();
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not reach the account database.");
        }
    }

    @Override public UserAccount create(String username, String displayName, String passwordHash, Role role) {
        try {
            List<UserAccount> rows = client.post().uri("/rest/v1/app_users")
                    .header("Prefer", "return=representation")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("username", username, "display_name", displayName,
                            "password_hash", passwordHash, "role", role.name()))
                    .retrieve().body(ROWS);
            if (rows == null || rows.isEmpty())
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The account was not saved.");
            return rows.get(0);
        } catch (RestClientResponseException ex) {
            // 409 is the unique constraint on username.
            if (ex.getStatusCode().value() == 409)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is taken.");
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not save the account.");
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not reach the account database.");
        }
    }
}
