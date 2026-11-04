package org.example.historyinaglimpse;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * "Continue with Google". Opens Google's sign-in page in the browser, waits on
 * a one-off local address for Google to send the user back with a code, then
 * hands the code to the backend, which finishes the sign-in.
 *
 * This is the standard sign-in flow for desktop apps (OAuth with PKCE and a
 * loopback redirect). The app never sees the user's Google password.
 */
public final class GoogleSignIn {
    /** Matches the backend's GoogleConfig. */
    public record Config(boolean enabled, String clientId) { }
    record CodeRequest(String code, String codeVerifier, String redirectUri) { }

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long WAIT_MINUTES = 3;
    private static volatile Consumer<String> browser = GoogleSignIn::openWithDesktop;
    private static volatile CompletableFuture<String> waiting;
    private static volatile Config config;

    private GoogleSignIn() { }

    /** The app passes its HostServices here so links open in the user's browser. */
    static void useBrowser(Consumer<String> opener) { browser = opener; }

    /** Google's sign-in page; override with -Dhistory.google.auth-url=... for testing. */
    private static String authUrl() {
        return System.getProperty("history.google.auth-url", "https://accounts.google.com/o/oauth2/v2/auth");
    }

    /** Blocking: whether the backend has Google sign-in set up. Remembered after the first answer. */
    public static boolean available() {
        Config known = config;
        if (known == null) {
            try {
                known = ApiClient.get("/api/auth/google", Config.class);
            } catch (ApiClient.ApiException ex) {
                return false;     // Backend down or older: just hide the button.
            }
            config = known;
        }
        return known.enabled() && known.clientId() != null;
    }

    /** Blocking: signs in with Google and returns the logged-in user. Call through {@link Session#async}. */
    static Session.LoginReply signIn() {
        if (!available()) throw new ApiClient.ApiException(0, "Google sign-in is not set up.");
        String verifier = randomText(48);
        String state = randomText(16);
        CompletableFuture<String> code = new CompletableFuture<>();
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException ex) {
            throw new ApiClient.ApiException(0, "Could not start Google sign-in.");
        }
        String redirect = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
        server.createContext("/", exchange -> answer(exchange, state, code));
        server.start();
        waiting = code;
        try {
            String link = authUrl() + "?" + query(Map.of(
                    "client_id", config.clientId(),
                    "redirect_uri", redirect,
                    "response_type", "code",
                    "scope", "openid email profile",
                    "code_challenge", challenge(verifier),
                    "code_challenge_method", "S256",
                    "state", state,
                    "prompt", "select_account"));
            Platform.runLater(() -> browser.accept(link));
            String received = code.get(WAIT_MINUTES, TimeUnit.MINUTES);
            return ApiClient.post("/api/auth/google", new CodeRequest(received, verifier, redirect),
                    Session.LoginReply.class);
        } catch (TimeoutException ex) {
            throw new ApiClient.ApiException(0, "Google sign-in timed out. Try again.");
        } catch (ExecutionException ex) {
            throw new ApiClient.ApiException(0, ex.getCause().getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiClient.ApiException(0, "Google sign-in was cancelled.");
        } finally {
            waiting = null;
            server.stop(0);
        }
    }

    /** Stops waiting for the browser, e.g. when the user closed the tab. */
    public static void cancel() {
        CompletableFuture<String> pending = waiting;
        if (pending != null) pending.completeExceptionally(new IllegalStateException("Google sign-in was cancelled."));
    }

    /** Handles Google sending the browser back to us. */
    private static void answer(HttpExchange exchange, String state, CompletableFuture<String> code) throws IOException {
        Map<String, String> params = parse(exchange.getRequestURI().getRawQuery());
        String page;
        if (!state.equals(params.get("state"))) {
            // Not our sign-in (a stray request, or a browser favicon fetch). Ignore it.
            page = "This link is not part of a sign-in.";
        } else if (params.containsKey("code")) {
            code.complete(params.get("code"));
            page = "You're signed in. You can close this tab and go back to History in a Glimpse.";
        } else {
            code.completeExceptionally(new IllegalStateException("Google sign-in was cancelled."));
            page = "Sign-in was cancelled. You can close this tab.";
        }
        byte[] body = ("<!doctype html><meta charset=utf-8><title>History in a Glimpse</title>"
                + "<body style=\"font-family:Georgia,serif;background:#f3e8d2;color:#493728;"
                + "display:grid;place-items:center;height:90vh;font-size:20px\"><p>" + page + "</p>")
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static String randomText(int bytes) {
        byte[] data = new byte[bytes];
        RANDOM.nextBytes(data);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    /** PKCE: Google gets the hash now and the backend proves it has the original later. */
    private static String challenge(String verifier) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String query(Map<String, String> params) {
        StringBuilder out = new StringBuilder();
        params.forEach((key, value) -> {
            if (!out.isEmpty()) out.append('&');
            out.append(key).append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"));
        });
        return out.toString();
    }

    private static Map<String, String> parse(String raw) {
        Map<String, String> params = new HashMap<>();
        if (raw == null) return params;
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) params.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return params;
    }

    private static void openWithDesktop(String url) {
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
        } catch (Exception ex) {
            System.err.println("Open this link to sign in with Google: " + url);
        }
    }
}
