package org.example.historyinaglimpse;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Shared JSON client for the Spring Boot backend. Adds the logged-in user's
 * token to every request, so teammates' screens get authentication for free.
 *
 * Calls block, so run them off the JavaFX thread (see {@link Session#async}).
 */
public final class ApiClient {
    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private ApiClient() { }

    /** Backend address; override with -Dhistory.animation.backend=... */
    public static String baseUrl() {
        return System.getProperty("history.animation.backend", "http://127.0.0.1:8080").replaceAll("/+$", "");
    }

    /** A request the backend refused, with the backend's message when it sent one. */
    public static final class ApiException extends RuntimeException {
        private final int status;
        ApiException(int status, String message) { super(message); this.status = status; }
        /** HTTP status, or 0 when the backend could not be reached. */
        public int status() { return status; }
    }

    public static <T> T get(String path, Class<T> type) {
        return send(request(path, Session.token()).GET(), type);
    }

    public static <T> T post(String path, Object body, Class<T> type) {
        return post(path, body, type, Session.token());
    }

    /** POST with a specific token instead of the current session's. */
    static <T> T post(String path, Object body, Class<T> type, String token) {
        try {
            String json = body == null ? "" : JSON.writeValueAsString(body);
            return send(request(path, token).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json)), type);
        } catch (IOException ex) {
            throw new ApiException(0, "Could not prepare the request.");
        }
    }

    private static HttpRequest.Builder request(String path, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .timeout(Duration.ofSeconds(20)).header("Accept", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return builder;
    }

    private static <T> T send(HttpRequest.Builder builder, Class<T> type) {
        HttpResponse<String> response;
        try {
            response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new ApiException(0, "Cannot reach the server. Is the backend running?");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiException(0, "The request was interrupted.");
        }
        int status = response.statusCode();
        if (status >= 400) throw new ApiException(status, errorMessage(response.body(), status));
        if (type == Void.class || response.body().isBlank()) return null;
        try {
            return JSON.readValue(response.body(), type);
        } catch (IOException ex) {
            throw new ApiException(status, "The server sent an unexpected reply.");
        }
    }

    /** Spring Boot error bodies look like {"status":401,"message":"..."}. */
    private static String errorMessage(String body, int status) {
        try {
            JsonNode node = JSON.readTree(body);
            String message = node.path("message").asText("");
            if (!message.isBlank()) return message;
        } catch (IOException | RuntimeException ignored) { }
        return "The server refused the request (HTTP " + status + ").";
    }
}
