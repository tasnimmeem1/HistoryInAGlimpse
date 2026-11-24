package com.historyinaglimpse.animation_backend;

import com.historyinaglimpse.animation_backend.auth.AuthService;
import com.historyinaglimpse.animation_backend.auth.Role;
import com.historyinaglimpse.animation_backend.auth.UserAccount.UserProfile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;

@Service
public class SavedSelectionService {
    private static final ParameterizedTypeReference<List<SavedSelection>> SELECTIONS =
            new ParameterizedTypeReference<>() { };
    private final RestClient client;
    private final AuthService auth;
    private final HistoricalRecordCatalog catalog;

    public SavedSelectionService(@Value("${supabase.url}") String url,
                                 @Value("${supabase.secret-key}") String key,
                                 AuthService auth,
                                 HistoricalRecordCatalog catalog) {
        if (key.isBlank()) {
            throw new IllegalStateException(
                    "SUPABASE_SECRET_KEY is missing. Add it to the AnimationBackendApplication run configuration.");
        }
        client = RestClient.builder().baseUrl(url).defaultHeader("apikey", key).build();
        this.auth = auth;
        this.catalog = catalog;
    }

    public List<SavedSelection> list(String authorization) {
        UserProfile user = auth.requireRole(authorization, Role.USER);
        try {
            List<SavedSelection> rows = client.get()
                    .uri("/rest/v1/saved_selections?select=record_id,saved_at&username=eq.{username}&order=saved_at.desc",
                            user.username())
                    .retrieve().body(SELECTIONS);
            return rows == null ? List.of() : rows;
        } catch (RestClientException ex) {
            throw databaseError("Could not load saved selections.");
        }
    }

    public SavedSelection save(String authorization, String recordId) {
        UserProfile user = auth.requireRole(authorization, Role.USER);
        if (recordId == null || catalog.find(recordId).isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Historical record not found.");
        try {
            List<SavedSelection> rows = client.post()
                    .uri("/rest/v1/saved_selections?on_conflict=username,record_id")
                    .header("Prefer", "resolution=merge-duplicates,return=representation")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("username", user.username(), "record_id", recordId))
                    .retrieve().body(SELECTIONS);
            if (rows == null || rows.isEmpty())
                throw databaseError("The selection was not saved.");
            return rows.get(0);
        } catch (RestClientException ex) {
            throw databaseError("Could not save the selection.");
        }
    }

    public void remove(String authorization, String recordId) {
        UserProfile user = auth.requireRole(authorization, Role.USER);
        if (recordId == null || catalog.find(recordId).isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Historical record not found.");
        try {
            List<SavedSelection> rows = client.delete()
                    .uri("/rest/v1/saved_selections?username=eq.{username}&record_id=eq.{recordId}",
                            user.username(), recordId)
                    .header("Prefer", "return=representation")
                    .retrieve().body(SELECTIONS);
            if (rows == null || rows.isEmpty())
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved selection not found.");
        } catch (RestClientException ex) {
            throw databaseError("Could not remove the saved selection.");
        }
    }

    private static ResponseStatusException databaseError(String message) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, message);
    }
}
