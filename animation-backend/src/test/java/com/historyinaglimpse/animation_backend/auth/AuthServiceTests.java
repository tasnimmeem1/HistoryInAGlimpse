package com.historyinaglimpse.animation_backend.auth;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTests {
    /** Keeps accounts in a map instead of Supabase. */
    static final class MemoryStore implements UserStore {
        final Map<String, UserAccount> rows = new HashMap<>();
        public Optional<UserAccount> findByUsername(String username) { return Optional.ofNullable(rows.get(username)); }
        public UserAccount create(String username, String displayName, String hash, Role role) {
            UserAccount account = new UserAccount((long) rows.size() + 1, username, displayName, hash, role);
            rows.put(username, account);
            return account;
        }
    }

    private final MemoryStore store = new MemoryStore();
    private final AuthService auth = new AuthService(store);

    private static HttpStatus status(Runnable action) {
        return HttpStatus.valueOf(assertThrows(ResponseStatusException.class, action::run).getStatusCode().value());
    }

    @Test void registerHashesPasswordAndDefaultsToUser() {
        var profile = auth.register("  Ada_L ", "Ada Lovelace", "analytical");
        assertEquals("ada_l", profile.username());
        assertEquals("Ada Lovelace", profile.displayName());
        assertEquals(Role.USER, profile.role());
        assertNotEquals("analytical", store.rows.get("ada_l").passwordHash());
    }

    @Test void registerRejectsBadInputAndDuplicates() {
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> auth.register("a!", null, "longenough")));
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> auth.register("ada", null, "short")));
        auth.register("ada", null, "longenough");
        assertEquals(HttpStatus.CONFLICT, status(() -> auth.register("ADA", null, "longenough")));
    }

    @Test void loginLogoutAndRoleChecks() {
        auth.register("ada", null, "longenough");
        assertEquals(HttpStatus.UNAUTHORIZED, status(() -> auth.login("ada", "wrong-password")));
        assertEquals(HttpStatus.UNAUTHORIZED, status(() -> auth.login("nobody", "longenough")));

        String header = "Bearer " + auth.login("Ada", "longenough").token();
        assertEquals("ada", auth.requireUser(header).username());
        assertEquals("ada", auth.requireRole(header, Role.USER).username());
        assertEquals(HttpStatus.FORBIDDEN, status(() -> auth.requireRole(header, Role.ADMIN)));

        auth.logout(header);
        assertEquals(HttpStatus.UNAUTHORIZED, status(() -> auth.requireUser(header)));
        assertEquals(HttpStatus.UNAUTHORIZED, status(() -> auth.requireUser(null)));
    }

    @Test void supabaseStoreReadsSnakeCaseRows() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<String> seen = new ArrayList<>();
        server.createContext("/rest/v1/app_users", e -> {
            seen.add(e.getRequestMethod() + " " + e.getRequestURI().getRawQuery() + " " + e.getRequestHeaders().getFirst("apikey"));
            byte[] body = ("[{\"id\":7,\"username\":\"ada\",\"display_name\":\"Ada\","
                    + "\"password_hash\":\"h\",\"role\":\"ADMIN\",\"created_at\":\"2026-10-28T00:00:00Z\"}]")
                    .getBytes(StandardCharsets.UTF_8);
            e.getResponseHeaders().add("Content-Type", "application/json");
            e.sendResponseHeaders(e.getRequestMethod().equals("POST") ? 201 : 200, body.length);
            e.getResponseBody().write(body);
            e.close();
        });
        server.start();
        try {
            var users = new SupabaseUserStore("http://127.0.0.1:" + server.getAddress().getPort(), "key");
            UserAccount found = users.findByUsername("ada").orElseThrow();
            assertEquals(new UserAccount(7L, "ada", "Ada", "h", Role.ADMIN), found);
            assertEquals(7L, users.create("ada", "Ada", "h", Role.USER).id());
            assertEquals("GET select=*&username=eq.ada key", seen.get(0));
            assertTrue(seen.get(1).startsWith("POST"));
        } finally {
            server.stop(0);
        }
    }
}
