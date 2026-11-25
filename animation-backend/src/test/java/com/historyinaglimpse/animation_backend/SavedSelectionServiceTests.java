package com.historyinaglimpse.animation_backend;

import com.historyinaglimpse.animation_backend.auth.AuthService;
import com.historyinaglimpse.animation_backend.auth.Role;
import com.historyinaglimpse.animation_backend.auth.UserAccount;
import com.historyinaglimpse.animation_backend.auth.UserStore;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SavedSelectionServiceTests {
    private static final class MemoryUsers implements UserStore {
        private final Map<String, UserAccount> users = new HashMap<>();
        public Optional<UserAccount> findByUsername(String username) { return Optional.ofNullable(users.get(username)); }
        public Optional<UserAccount> findByGoogleSub(String sub) { return Optional.empty(); }
        public UserAccount create(String username, String displayName, String hash, String sub, Role role) {
            UserAccount user = new UserAccount(1L, username, displayName, hash, sub, role);
            users.put(username, user);
            return user;
        }
        public List<UserAccount> findAll() { return List.copyOf(users.values()); }
        public UserAccount update(String username, Map<String, String> columns) { return users.get(username); }
    }

    @Test
    void savesListsAndRemovesSelectionsOnlyForLoggedInUsers() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<String> requests = new ArrayList<>();
        server.createContext("/rest/v1/saved_selections", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getRawQuery());
            byte[] body = "[{\"record_id\":\"stone-age-life-10000\",\"saved_at\":\"2026-11-23T00:00:00Z\"}]"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            MemoryUsers users = new MemoryUsers();
            AuthService auth = new AuthService(users);
            auth.register("ada", "Ada", "longenough");
            String authorization = "Bearer " + auth.login("ada", "longenough").token();
            SavedSelectionService service = new SavedSelectionService(
                    "http://127.0.0.1:" + server.getAddress().getPort(), "key",
                    auth, new HistoricalRecordCatalog());

            assertEquals("stone-age-life-10000", service.list(authorization).get(0).recordId());
            assertEquals("stone-age-life-10000", service.save(authorization, "stone-age-life-10000").recordId());
            assertDoesNotThrow(() -> service.remove(authorization, "stone-age-life-10000"));
            assertEquals(HttpStatus.NOT_FOUND, status(() -> service.save(authorization, "unknown")));
            assertEquals(HttpStatus.UNAUTHORIZED, status(() -> service.list(null)));
            assertTrue(requests.get(0).contains("username=eq.ada"));
            assertTrue(requests.get(1).startsWith("POST on_conflict=username,record_id"));
            assertTrue(requests.get(2).contains("record_id=eq.stone-age-life-10000"));
        } finally {
            server.stop(0);
        }
    }

    private static HttpStatus status(Runnable action) {
        return HttpStatus.valueOf(assertThrows(ResponseStatusException.class, action::run).getStatusCode().value());
    }
}
