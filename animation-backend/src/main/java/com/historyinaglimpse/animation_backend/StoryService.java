package com.historyinaglimpse.animation_backend;

import com.historyinaglimpse.animation_backend.auth.AuthService;
import com.historyinaglimpse.animation_backend.auth.Role;
import com.historyinaglimpse.animation_backend.auth.UserAccount.UserProfile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class StoryService {
    private static final RowMapper<Story> STORY_ROW = (result, row) -> new Story(
            result.getLong("id"),
            result.getString("author_username"),
            result.getString("author_display_name"),
            result.getString("title"),
            result.getString("content"),
            result.getString("tags"),
            result.getString("created_at"),
            result.getString("updated_at"));

    private final JdbcTemplate database;
    private final AuthService auth;

    public StoryService(JdbcTemplate database, AuthService auth) {
        this.database = database;
        this.auth = auth;
    }

    public List<Story> list(String authorization) {
        auth.requireRole(authorization, Role.USER);
        return database.query("SELECT * FROM shared_stories ORDER BY created_at DESC, id DESC", STORY_ROW);
    }

    public Story create(String authorization, StoryController.StoryRequest request) {
        UserProfile user = auth.requireRole(authorization, Role.USER);
        validate(request);
        String now = OffsetDateTime.now().toString();
        KeyHolder key = new GeneratedKeyHolder();
        database.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO shared_stories "
                            + "(author_username, author_display_name, title, content, tags, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, user.username());
            statement.setString(2, displayName(user));
            statement.setString(3, request.title().strip());
            statement.setString(4, request.content().strip());
            statement.setString(5, cleanTags(request.tags()));
            statement.setString(6, now);
            statement.setString(7, now);
            return statement;
        }, key);
        Number id = key.getKey();
        if (id == null) throw new IllegalStateException("The story was inserted but its id was not returned.");
        return find(id.longValue());
    }

    public Story update(String authorization, long id, StoryController.StoryRequest request) {
        UserProfile user = auth.requireRole(authorization, Role.USER);
        validate(request);
        int changed = database.update(
                "UPDATE shared_stories SET author_display_name = ?, title = ?, content = ?, tags = ?, updated_at = ? "
                        + "WHERE id = ? AND author_username = ?",
                displayName(user), request.title().strip(), request.content().strip(),
                cleanTags(request.tags()), OffsetDateTime.now().toString(), id, user.username());
        if (changed == 0) throw notFound();
        return find(id);
    }

    public void delete(String authorization, long id) {
        UserProfile user = auth.requireRole(authorization, Role.USER);
        int changed = database.update("DELETE FROM shared_stories WHERE id = ? AND author_username = ?",
                id, user.username());
        if (changed == 0) throw notFound();
    }

    private Story find(long id) {
        return database.query("SELECT * FROM shared_stories WHERE id = ?", STORY_ROW, id).stream()
                .findFirst().orElseThrow(() -> new IllegalStateException("The saved story could not be reloaded."));
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

    private static String displayName(UserProfile user) {
        return user.displayName() == null || user.displayName().isBlank()
                ? user.username() : user.displayName();
    }

    private static String cleanTags(String tags) {
        return tags == null ? "" : tags.strip();
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Story not found, or it belongs to another user.");
    }
}
