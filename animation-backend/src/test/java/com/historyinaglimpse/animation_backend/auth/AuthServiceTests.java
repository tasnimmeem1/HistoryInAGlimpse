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
        public List<UserAccount> findAll() { return List.copyOf(rows.values()); }
        public UserAccount update(String username, Map<String, String> columns) {
            UserAccount a = rows.get(username);
            if (a == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            UserAccount changed = new UserAccount(a.id(), a.username(),
                    columns.getOrDefault("display_name", a.displayName()),
                    columns.getOrDefault("password_hash", a.passwordHash()),
                    columns.containsKey("role") ? Role.valueOf(columns.get("role")) : a.role());
            rows.put(username, changed);
            return changed;
        }
    }

    /** Registers, logs in, and returns the Authorization header. */
    private String loggedIn(String name) {
        auth.register(name, null, "longenough");
        return "Bearer " + auth.login(name, "longenough").token();
    }

    private String admin(String name) {
        loggedIn(name);
        store.update(name, Map.of("role", "ADMIN"));
        // Log in again so the session carries the new role.
        return "Bearer " + auth.login(name, "longenough").token();
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

    @Test void onlyAdminsListUsersAndChangeRoles() {
        String user = loggedIn("ada");
        String boss = admin("grace");
        assertEquals(HttpStatus.FORBIDDEN, status(() -> auth.listUsers(user)));
        assertEquals(HttpStatus.FORBIDDEN, status(() -> auth.setRole(user, "grace", Role.USER)));
        assertEquals(2, auth.listUsers(boss).size());

        assertEquals(Role.ADMIN, auth.setRole(boss, "ADA", Role.ADMIN).role());
        // Ada's open session picks up the new role without logging in again.
        assertEquals(Role.ADMIN, auth.requireUser(user).role());
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> auth.setRole(boss, "grace", Role.USER)));
        assertEquals(HttpStatus.NOT_FOUND, status(() -> auth.setRole(boss, "nobody", Role.USER)));
    }

    @Test void usersEditTheirOwnNameAndPassword() {
        String header = loggedIn("ada");
        assertEquals("Ada L", auth.updateProfile(header, "  Ada L ").displayName());
        assertEquals("Ada L", auth.requireUser(header).displayName());
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> auth.updateProfile(header, " ")));

        assertEquals(HttpStatus.BAD_REQUEST, status(() -> auth.changePassword(header, "wrong-one", "brandnew123")));
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> auth.changePassword(header, "longenough", "short")));
        auth.changePassword(header, "longenough", "brandnew123");
        assertEquals(HttpStatus.UNAUTHORIZED, status(() -> auth.login("ada", "longenough")));
        assertNotNull(auth.login("ada", "brandnew123").token());
        assertEquals(HttpStatus.UNAUTHORIZED, status(() -> auth.updateProfile(null, "x")));
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
            assertEquals(1, users.findAll().size());
            assertEquals("GET select=*&order=id.asc key", seen.get(2));
            assertEquals(Role.ADMIN, users.update("ada", Map.of("role", "ADMIN")).role());
            assertEquals("PATCH username=eq.ada key", seen.get(3));
        } finally {
            server.stop(0);
        }
    }
}
