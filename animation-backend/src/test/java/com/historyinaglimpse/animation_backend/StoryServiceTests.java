package com.historyinaglimpse.animation_backend;

import com.historyinaglimpse.animation_backend.auth.AuthService;
import com.historyinaglimpse.animation_backend.auth.Role;
import com.historyinaglimpse.animation_backend.auth.UserAccount;
import com.historyinaglimpse.animation_backend.auth.UserStore;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StoryServiceTests {
    private static final class MemoryUsers implements UserStore {
        private final Map<String, UserAccount> users = new HashMap<>();
        private long nextId = 1;
        public Optional<UserAccount> findByUsername(String username) { return Optional.ofNullable(users.get(username)); }
        public Optional<UserAccount> findByGoogleSub(String sub) { return Optional.empty(); }
        public UserAccount create(String username, String displayName, String hash, String sub, Role role) {
            UserAccount user = new UserAccount(nextId++, username, displayName, hash, sub, role);
            users.put(username, user);
            return user;
        }
        public List<UserAccount> findAll() { return List.copyOf(users.values()); }
        public UserAccount update(String username, Map<String, String> columns) { return users.get(username); }
    }

    private StoryService stories;
    private String ada;
    private String grace;

    @BeforeEach
    void setUp() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:stories-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        JdbcTemplate database = new JdbcTemplate(source);
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(source);
        MemoryUsers users = new MemoryUsers();
        AuthService auth = new AuthService(users);
        auth.register("ada", "Ada", "longenough");
        auth.register("grace", "Grace", "longenough");
        ada = "Bearer " + auth.login("ada", "longenough").token();
        grace = "Bearer " + auth.login("grace", "longenough").token();
        stories = new StoryService(database, auth);
    }

    @Test
    void createsListsUpdatesAndDeletesStoriesWithAuthorChecks() {
        StoryController.StoryRequest input = new StoryController.StoryRequest(
                " A story ", " History happened. ", " ancient ");
        Story created = stories.create(ada, input);
        assertEquals("ada", created.authorUsername());
        assertEquals("Ada", created.authorDisplayName());
        assertEquals("A story", created.title());
        assertEquals("History happened.", created.content());
        assertEquals("ancient", created.tags());
        assertEquals(created.createdAt(), created.updatedAt());
        assertEquals(List.of(created), stories.list(ada));
        assertEquals(List.of(created), stories.list(grace));

        Story updated = stories.update(ada, created.id(),
                new StoryController.StoryRequest("Updated", "Changed text", "roman"));
        assertEquals("Updated", updated.title());
        assertEquals("Ada", updated.authorDisplayName());
        assertEquals(created.createdAt(), updated.createdAt());
        assertNotEquals(created.updatedAt(), updated.updatedAt());
        assertEquals(1, stories.list(ada).size());

        assertEquals(HttpStatus.NOT_FOUND, status(() -> stories.update(grace, created.id(), input)));
        assertEquals(HttpStatus.NOT_FOUND, status(() -> stories.delete(grace, created.id())));
        stories.delete(ada, created.id());
        assertTrue(stories.list(ada).isEmpty());
    }

    @Test
    void validatesStoryInputAndRequiresLogin() {
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> stories.create(ada,
                new StoryController.StoryRequest("", "content", ""))));
        assertEquals(HttpStatus.UNAUTHORIZED, status(() -> stories.list(null)));
    }

    private static HttpStatus status(Runnable action) {
        return HttpStatus.valueOf(assertThrows(ResponseStatusException.class, action::run).getStatusCode().value());
    }
}
