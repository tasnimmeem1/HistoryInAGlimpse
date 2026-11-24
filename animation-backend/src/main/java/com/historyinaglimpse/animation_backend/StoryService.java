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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Service
public class StoryService {
    private static final ParameterizedTypeReference<List<Story>> STORIES = new ParameterizedTypeReference<>() { };
    private final RestClient client;
    private final AuthService auth;

    public StoryService(@Value("${supabase.url}") String url,
                        @Value("${supabase.secret-key}") String key,
                        AuthService auth) {
        if (key.isBlank()) {
            throw new IllegalStateException(
                    "SUPABASE_SECRET_KEY is missing. Add it to the AnimationBackendApplication run configuration.");
        }
        this.client = RestClient.builder().baseUrl(url).defaultHeader("apikey", key).build();
        this.auth = auth;
    }

    public List<Story> list(String authorization) {
        auth.requireRole(authorization, Role.USER);
        try {
            List<Story> rows = client.get()
                    .uri("/rest/v1/shared_stories?select=*&order=created_at.desc")
                    .retrieve().body(STORIES);
            return rows == null ? List.of() : rows;
        } catch (RestClientException ex) {
            throw databaseError("Could not load shared stories.");
        }
    }

    public Story create(String authorization, StoryController.StoryRequest request) {
        UserProfile user = auth.requireRole(authorization, Role.USER);
        validate(request);
        Map<String, String> row = Map.of(
                "author_username", user.username(),
                "author_display_name", user.displayName(),
                "title", request.title().strip(),
                "content", request.content().strip(),
                "tags", cleanTags(request.tags()));
        try {
            List<Story> rows = client.post().uri("/rest/v1/shared_stories")
                    .header("Prefer", "return=representation")
                    .contentType(MediaType.APPLICATION_JSON).body(row)
                    .retrieve().body(STORIES);
            if (rows == null || rows.isEmpty()) throw databaseError("The story was not saved.");
            return rows.get(0);
        } catch (RestClientException ex) {
            throw databaseError("Could not save the story.");
        }
    }

    public Story update(String authorization, long id, StoryController.StoryRequest request) {
        UserProfile user = auth.requireRole(authorization, Role.USER);
        validate(request);
        try {
            List<Story> rows = client.patch()
                    .uri("/rest/v1/shared_stories?id=eq.{id}&author_username=eq.{username}", id, user.username())
                    .header("Prefer", "return=representation")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("author_display_name", user.displayName(),
                            "title", request.title().strip(), "content", request.content().strip(),
                            "tags", cleanTags(request.tags()), "updated_at", OffsetDateTime.now().toString()))
                    .retrieve().body(STORIES);
            if (rows == null || rows.isEmpty()) throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Story not found, or it belongs to another user.");
            return rows.get(0);
        } catch (RestClientException ex) {
            throw databaseError("Could not update the story.");
        }
    }

    public void delete(String authorization, long id) {
        UserProfile user = auth.requireRole(authorization, Role.USER);
        try {
            List<Story> rows = client.delete()
                    .uri("/rest/v1/shared_stories?id=eq.{id}&author_username=eq.{username}", id, user.username())
                    .header("Prefer", "return=representation")
                    .retrieve().body(STORIES);
            if (rows == null || rows.isEmpty()) throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Story not found, or it belongs to another user.");
        } catch (RestClientException ex) {
            throw databaseError("Could not delete the story.");
        }
    }

    private static void validate(StoryController.StoryRequest request) {
        if (request == null || request.title() == null || request.title().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a story title.");
        if (request.title().strip().length() > 120)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Titles are at most 120 characters.");
        if (request.content() == null || request.content().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the story.");
        if (request.content().strip().length() > 10000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stories are at most 10,000 characters.");
        if (request.tags() != null && request.tags().strip().length() > 200)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tags are at most 200 characters.");
    }

    private static String cleanTags(String tags) {
        return tags == null ? "" : tags.strip();
    }

    private static ResponseStatusException databaseError(String message) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, message);
    }
}
