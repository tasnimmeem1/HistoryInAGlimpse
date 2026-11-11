package com.historyinaglimpse.animation_backend.auth;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** Runs the store against a fake Supabase API to check what it sends and how it reports setup mistakes. */
class SupabaseUserStoreTests {
    private HttpServer server;
    private final AtomicReference<String> sentBody = new AtomicReference<>();

    /** Starts a fake Supabase that answers every request with this status and body. */
    private SupabaseUserStore storeAnswering(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            sentBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return new SupabaseUserStore("http://127.0.0.1:" + server.getAddress().getPort(), "test-key");
    }

    @AfterEach void stop() { if (server != null) server.stop(0); }

    private static String messageOf(Runnable action) {
        return assertThrows(ResponseStatusException.class, action::run).getReason();
    }

    @Test void passwordSignUpLeavesOutTheGoogleColumn() throws IOException {
        var store = storeAnswering(201, """
                [{"id":1,"username":"ada","display_name":"Ada","password_hash":"h","role":"USER"}]""");
        var account = store.create("ada", "Ada", "h", null, Role.USER);
        assertEquals("ada", account.username());
        assertFalse(sentBody.get().contains("google_sub"), sentBody.get());
        assertTrue(sentBody.get().contains("password_hash"));
    }

    @Test void googleSignUpLeavesOutThePasswordColumn() throws IOException {
        var store = storeAnswering(201, """
                [{"id":1,"username":"ada","display_name":"Ada","google_sub":"g1","role":"USER"}]""");
        store.create("ada", "Ada", null, "g1", Role.USER);
        assertFalse(sentBody.get().contains("password_hash"), sentBody.get());
    }

    @Test void missingTableSaysToRunTheScript() throws IOException {
        var store = storeAnswering(404, """
                {"code":"PGRST205","message":"Could not find the table 'public.app_users' in the schema cache"}""");
        assertTrue(messageOf(() -> store.findByUsername("ada")).contains("Run sql/app_users.sql"));
    }

    @Test void oldTableSaysToRunTheScriptAgain() throws IOException {
        var store = storeAnswering(400, """
                {"code":"PGRST204","message":"Could not find the 'google_sub' column of 'app_users' in the schema cache"}""");
        assertTrue(messageOf(() -> store.create("bob", "Bob", null, "g1", Role.USER)).contains("again"));
    }

    @Test void wrongKeySaysToCheckTheKey() throws IOException {
        var store = storeAnswering(401, """
                {"message":"Invalid API key"}""");
        assertTrue(messageOf(() -> store.findByUsername("ada")).contains("SUPABASE_SECRET_KEY"));
    }

    @Test void takenUsernameIsStillAConflict() throws IOException {
        var store = storeAnswering(409, """
                {"code":"23505","message":"duplicate key value violates unique constraint"}""");
        assertEquals("That username is taken.", messageOf(() -> store.create("ada", "Ada", "h", null, Role.USER)));
    }
}
