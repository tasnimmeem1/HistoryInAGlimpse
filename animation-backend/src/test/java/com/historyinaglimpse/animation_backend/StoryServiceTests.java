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

class StoryServiceTests {
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
    void supportsStoryCrudAndRestrictsMutationsToTheAuthor() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<String> requests = new ArrayList<>();
        server.createContext("/rest/v1/shared_stories", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getRawQuery());
            byte[] body = ("[{\"id\":9,\"author_username\":\"ada\",\"author_display_name\":\"Ada\","
                    + "\"title\":\"A story\",\"content\":\"History happened.\",\"tags\":\"ancient\","
                    + "\"created_at\":\"2026-11-23T00:00:00Z\",\"updated_at\":\"2026-11-23T00:00:00Z\"}]")
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
            StoryService service = new StoryService(
                    "http://127.0.0.1:" + server.getAddress().getPort(), "key", auth);

            assertEquals(9L, service.list(authorization).get(0).id());
            StoryController.StoryRequest input = new StoryController.StoryRequest(
                    " A story ", " History happened. ", " ancient ");
            assertEquals("ada", service.create(authorization, input).authorUsername());
            assertEquals(9L, service.update(authorization, 9L, input).id());
            assertDoesNotThrow(() -> service.delete(authorization, 9L));

            assertEquals("GET select=*&order=created_at.desc", requests.get(0));
            assertTrue(requests.get(1).startsWith("POST null"));
            assertEquals("PATCH id=eq.9&author_username=eq.ada", requests.get(2));
            assertEquals("DELETE id=eq.9&author_username=eq.ada", requests.get(3));
            assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.create(authorization,
                    new StoryController.StoryRequest("", "content", ""))));
            assertEquals(HttpStatus.UNAUTHORIZED, status(() -> service.list(null)));
        } finally {
            server.stop(0);
        }
    }

    private static HttpStatus status(Runnable action) {
        return HttpStatus.valueOf(assertThrows(ResponseStatusException.class, action::run).getStatusCode().value());
    }
}
