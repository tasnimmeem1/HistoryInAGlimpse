package org.example.historyinaglimpse;

import javafx.application.Platform;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** The logged-in user for this run of the app, plus the account calls to the backend. */
public final class Session {
    /** Matches the backend's UserProfile. */
    public record User(Long id, String username, String displayName, Role role,
                       boolean hasPassword, boolean google) { }
    public record LoginReply(String token, User user) { }
    public record LoginRequest(String username, String password) { }
    public record RegisterRequest(String username, String displayName, String password) { }
    public record ProfileRequest(String displayName) { }
    public record PasswordRequest(String currentPassword, String newPassword) { }
    public record RoleRequest(Role role) { }
    public record HistoricalRecord(String id, String title, int year, int lane, String date,
                                   String description, String source) { }
    public record SavedSelection(String recordId, String savedAt) { }
    public record SaveSelectionRequest(String recordId) { }
    public record Story(Long id, String authorUsername, String authorDisplayName, String title,
                        String content, String tags, String createdAt, String updatedAt) { }
    public record StoryRequest(String title, String content, String tags) { }

    private static volatile User user;
    private static volatile String token;

    private Session() { }

    /** The current user, or null for a guest. */
    public static User user() { return user; }
    static String token() { return token; }
    public static boolean loggedIn() { return user != null; }
    public static Role role() { return user == null ? Role.GUEST : user.role(); }
    public static boolean allows(Role required) { return role().atLeast(required); }

    public static String displayName() {
        if (user == null) return "Guest";
        return user.displayName() == null || user.displayName().isBlank() ? user.username() : user.displayName();
    }

    /** Blocking: call through {@link #async}. */
    public static User login(String username, String password) {
        LoginReply reply = ApiClient.post("/api/auth/login", new LoginRequest(username, password), LoginReply.class);
        token = reply.token();
        user = reply.user();
        return user;
    }

    /** Blocking: signs in through Google in the browser. */
    public static User loginWithGoogle() {
        LoginReply reply = GoogleSignIn.signIn();
        token = reply.token();
        user = reply.user();
        return user;
    }

    /** Blocking: creates the account, then logs in with it. */
    public static User register(String username, String displayName, String password) {
        ApiClient.post("/api/auth/register", new RegisterRequest(username, displayName, password), User.class);
        return login(username, password);
    }

    /** Blocking: changes the current user's display name. */
    public static User updateDisplayName(String displayName) {
        user = ApiClient.put("/api/profiles/me", new ProfileRequest(displayName), User.class);
        return user;
    }

    public static User refreshProfile() {
        user = ApiClient.get("/api/profiles/me", User.class);
        return user;
    }

    /** Blocking: changes the current user's password. */
    public static void changePassword(String currentPassword, String newPassword) {
        ApiClient.put("/api/auth/me/password", new PasswordRequest(currentPassword, newPassword), Void.class);
        User current = user;
        if (current != null && !current.hasPassword())
            user = new User(current.id(), current.username(), current.displayName(), current.role(), true, current.google());
    }

    /** Blocking, admins only: every account. */
    public static java.util.List<User> listUsers() {
        return java.util.List.of(ApiClient.get("/api/admin/users", User[].class));
    }

    /** Blocking, admins only: gives another account a new role. */
    public static User setRole(String username, Role role) {
        return ApiClient.put("/api/admin/users/" + java.net.URLEncoder.encode(username, java.nio.charset.StandardCharsets.UTF_8)
                + "/role", new RoleRequest(role), User.class);
    }

    public static java.util.List<Story> listStories() {
        return java.util.List.of(ApiClient.get("/api/contributions", Story[].class));
    }

    public static Story createStory(String title, String content, String tags) {
        return ApiClient.post("/api/contributions", new StoryRequest(title, content, tags), Story.class);
    }

    public static Story updateStory(long id, String title, String content, String tags) {
        return ApiClient.put("/api/contributions/" + id, new StoryRequest(title, content, tags), Story.class);
    }

    public static void deleteStory(long id) {
        ApiClient.delete("/api/contributions/" + id, Void.class);
    }

    public static java.util.List<HistoricalRecord> listHistoricalRecords() {
        return java.util.List.of(ApiClient.get("/api/historical-records", HistoricalRecord[].class));
    }

    public static java.util.List<SavedSelection> listSavedSelections() {
        return java.util.List.of(ApiClient.get("/api/saved-selections", SavedSelection[].class));
    }

    public static SavedSelection saveSelection(String recordId) {
        return ApiClient.post("/api/saved-selections", new SaveSelectionRequest(recordId), SavedSelection.class);
    }

    public static void removeSelection(String recordId) {
        ApiClient.delete("/api/saved-selections/" + recordId, Void.class);
    }

    /** Forgets the user right away and tells the backend in the background. */
    public static void logout() {
        String old = token;
        user = null;
        token = null;
        if (old != null) CompletableFuture.runAsync(() -> {
            try {
                ApiClient.post("/api/auth/logout", null, Void.class, old);
            } catch (ApiClient.ApiException ignored) {
                // The token is gone locally; the backend drops it on restart anyway.
            }
        });
    }

    /**
     * Runs a blocking backend call off the JavaFX thread and hands the result,
     * or the error message, back on the JavaFX thread.
     */
    public static <T> void async(Supplier<T> call, Consumer<T> onSuccess, Consumer<String> onError) {
        CompletableFuture.supplyAsync(call).whenComplete((result, error) -> Platform.runLater(() -> {
            if (error == null) { onSuccess.accept(result); return; }
            Throwable cause = error.getCause() != null ? error.getCause() : error;
            onError.accept(cause instanceof ApiClient.ApiException api ? api.getMessage()
                    : "Something went wrong: " + cause.getMessage());
        }));
    }
}
