package com.historyinaglimpse.animation_backend;

import com.historyinaglimpse.animation_backend.auth.AuthService;
import com.historyinaglimpse.animation_backend.auth.Role;
import com.historyinaglimpse.animation_backend.auth.UserAccount.UserProfile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.Arrays;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class StoryService {
    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final List<String> IMAGE_TYPES = List.of("image/png", "image/jpeg", "image/gif");
    private static final RowMapper<Story> STORY_ROW = (result, row) -> {
        double latitude = result.getDouble("latitude");
        Double lat = result.wasNull() ? null : latitude;
        double longitude = result.getDouble("longitude");
        Double lon = result.wasNull() ? null : longitude;
        return new Story(result.getLong("id"), result.getString("author_username"),
                result.getString("author_display_name"), result.getString("title"),
                result.getString("content"), result.getString("tags"),
                result.getString("historical_date"), result.getString("place_name"),
                lat, lon, result.getString("image_content_type") != null,
                result.getString("created_at"), result.getString("updated_at"));
    };

    public record StoryImage(String contentType, byte[] data) { }

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
                            + "(author_username, author_display_name, title, content, tags, historical_date, "
                            + "place_name, latitude, longitude, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, user.username());
            statement.setString(2, displayName(user));
            statement.setString(3, request.title().strip());
            statement.setString(4, request.content().strip());
            statement.setString(5, cleanTags(request.tags()));
            statement.setString(6, clean(request.historicalDate()));
            statement.setString(7, clean(request.placeName()));
            if (request.latitude() == null) statement.setNull(8, java.sql.Types.DOUBLE);
            else statement.setDouble(8, request.latitude());
            if (request.longitude() == null) statement.setNull(9, java.sql.Types.DOUBLE);
            else statement.setDouble(9, request.longitude());
            statement.setString(10, now);
            statement.setString(11, now);
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
                "UPDATE shared_stories SET author_display_name = ?, title = ?, content = ?, tags = ?, "
                        + "historical_date = ?, place_name = ?, latitude = ?, longitude = ?, updated_at = ? "
                        + "WHERE id = ? AND author_username = ?",
                displayName(user), request.title().strip(), request.content().strip(),
                cleanTags(request.tags()), clean(request.historicalDate()), clean(request.placeName()),
                request.latitude(), request.longitude(), OffsetDateTime.now().toString(), id, user.username());
        if (changed == 0) throw notFound();
        return find(id);
    }

    public void delete(String authorization, long id) {
        UserProfile user = auth.requireRole(authorization, Role.USER);
        int changed = database.update("DELETE FROM shared_stories WHERE id = ? AND author_username = ?",
                id, user.username());
        if (changed == 0) throw notFound();
    }

    public Story uploadImage(String authorization, long id, String contentType, byte[] data) {
        UserProfile user = auth.requireRole(authorization, Role.USER);
        validateImage(contentType, data);
        int changed = database.update(
                "UPDATE shared_stories SET image_content_type = ?, image_data = ?, updated_at = ? "
                        + "WHERE id = ? AND author_username = ?",
                contentType.toLowerCase(java.util.Locale.ROOT), data,
                OffsetDateTime.now().toString(), id, user.username());
        if (changed == 0) throw notFound();
        return find(id);
    }

    public StoryImage image(String authorization, long id) {
        auth.requireRole(authorization, Role.USER);
        try {
            return database.queryForObject(
                    "SELECT image_content_type, image_data FROM shared_stories WHERE id = ? AND image_data IS NOT NULL",
                    (result, row) -> new StoryImage(result.getString("image_content_type"),
                            result.getBytes("image_data")), id);
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This story has no image.");
        }
    }

    public void removeImage(String authorization, long id) {
        UserProfile user = auth.requireRole(authorization, Role.USER);
        int changed = database.update(
                "UPDATE shared_stories SET image_content_type = NULL, image_data = NULL, updated_at = ? "
                        + "WHERE id = ? AND author_username = ? AND image_data IS NOT NULL",
                OffsetDateTime.now().toString(), id, user.username());
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
        if (request.historicalDate() == null || request.historicalDate().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the historical date or time.");
        if (request.historicalDate().strip().length() > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Historical dates are at most 100 characters.");
        if (request.placeName() != null && request.placeName().strip().length() > 120)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Place names are at most 120 characters.");
        boolean hasLatitude = request.latitude() != null;
        boolean hasLongitude = request.longitude() != null;
        if (hasLatitude != hasLongitude)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter both map coordinates.");
        if (hasLatitude && (request.latitude() < -90 || request.latitude() > 90
                || request.longitude() < -180 || request.longitude() > 180))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coordinates are outside the map range.");
        if (hasLatitude && (request.placeName() == null || request.placeName().isBlank()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name the place for this map pin.");
    }

    private static String displayName(UserProfile user) {
        return user.displayName() == null || user.displayName().isBlank()
                ? user.username() : user.displayName();
    }

    private static String cleanTags(String tags) {
        return tags == null ? "" : tags.strip();
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private static void validateImage(String contentType, byte[] data) {
        if (contentType == null || !IMAGE_TYPES.contains(contentType.toLowerCase(java.util.Locale.ROOT)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a PNG, JPEG, or GIF image.");
        if (data == null || data.length == 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose an image to upload.");
        if (data.length > MAX_IMAGE_BYTES)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Images must be 5 MB or smaller.");
        boolean valid = switch (contentType.toLowerCase(java.util.Locale.ROOT)) {
            case "image/png" -> data.length >= 8 && Arrays.equals(
                    Arrays.copyOf(data, 8), new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
            case "image/jpeg" -> data.length >= 3 && (data[0] & 0xff) == 0xff
                    && (data[1] & 0xff) == 0xd8 && (data[2] & 0xff) == 0xff;
            case "image/gif" -> data.length >= 6 && (new String(data, 0, 6, java.nio.charset.StandardCharsets.US_ASCII)
                    .equals("GIF87a") || new String(data, 0, 6,
                    java.nio.charset.StandardCharsets.US_ASCII).equals("GIF89a"));
            default -> false;
        };
        if (!valid) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file is not a valid image.");
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Story not found, or it belongs to another user.");
    }
}
