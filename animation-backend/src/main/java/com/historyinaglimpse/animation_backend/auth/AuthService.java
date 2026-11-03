package com.historyinaglimpse.animation_backend.auth;

import com.historyinaglimpse.animation_backend.auth.UserAccount.UserProfile;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Registration, login and logout. Sessions are random bearer tokens kept in
 * memory, so everyone is logged out when the backend restarts.
 *
 * Other controllers protect an endpoint with {@link #requireRole}, passing the
 * request's Authorization header.
 */
@Service
public class AuthService {
    private static final Pattern USERNAME = Pattern.compile("[a-z0-9_]{3,30}");
    private static final int MIN_PASSWORD = 8;

    private final UserStore users;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();
    private final Map<String, UserProfile> sessions = new ConcurrentHashMap<>();

    public AuthService(UserStore users) { this.users = users; }

    public record Login(String token, UserProfile user) { }

    public UserProfile register(String username, String displayName, String password) {
        String name = normalize(username);
        if (!USERNAME.matcher(name).matches())
            throw bad("Usernames are 3 to 30 letters, numbers or underscores.");
        if (password == null || password.length() < MIN_PASSWORD)
            throw bad("Passwords need at least " + MIN_PASSWORD + " characters.");
        String shown = displayName == null || displayName.isBlank() ? name : displayName.strip();
        if (users.findByUsername(name).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is taken.");
        // New accounts are always USER; promote admins in the database.
        return users.create(name, shown, encoder.encode(password), null, Role.USER).profile();
    }

    public Login login(String username, String password) {
        UserAccount account = users.findByUsername(normalize(username))
                .filter(a -> password != null && a.passwordHash() != null && encoder.matches(password, a.passwordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Wrong username or password."));
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, account.profile());
        return new Login(token, account.profile());
    }

    /** Admins only: every account, oldest first. */
    public List<UserProfile> listUsers(String authorization) {
        requireRole(authorization, Role.ADMIN);
        return users.findAll().stream().map(UserAccount::profile).toList();
    }

    /** Admins only. Admins cannot change their own role, so there is always one left. */
    public UserProfile setRole(String authorization, String username, Role role) {
        UserProfile admin = requireRole(authorization, Role.ADMIN);
        if (role == null) throw bad("Choose a role.");
        String name = normalize(username);
        if (name.equals(admin.username())) throw bad("You cannot change your own role.");
        return refresh(users.update(name, Map.of("role", role.name())).profile());
    }

    /** Changes the logged-in user's display name. */
    public UserProfile updateProfile(String authorization, String displayName) {
        UserProfile user = requireUser(authorization);
        if (displayName == null || displayName.isBlank()) throw bad("Enter a display name.");
        if (displayName.strip().length() > 60) throw bad("Display names are at most 60 characters.");
        return refresh(users.update(user.username(), Map.of("display_name", displayName.strip())).profile());
    }

    /** Changes the logged-in user's password after checking the current one. */
    public void changePassword(String authorization, String currentPassword, String newPassword) {
        UserProfile user = requireUser(authorization);
        UserAccount account = users.findByUsername(user.username())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in."));
        if (currentPassword == null || !encoder.matches(currentPassword, account.passwordHash()))
            throw bad("Your current password is wrong.");
        if (newPassword == null || newPassword.length() < MIN_PASSWORD)
            throw bad("Passwords need at least " + MIN_PASSWORD + " characters.");
        users.update(user.username(), Map.of("password_hash", encoder.encode(newPassword)));
    }

    /** Puts a changed profile into that user's open sessions, so role changes apply at once. */
    private UserProfile refresh(UserProfile changed) {
        sessions.replaceAll((token, profile) -> profile.username().equals(changed.username()) ? changed : profile);
        return changed;
    }

    public void logout(String authorization) {
        String token = token(authorization);
        if (token != null) sessions.remove(token);
    }

    /** The logged-in user for an Authorization header, or 401. */
    public UserProfile requireUser(String authorization) {
        String token = token(authorization);
        UserProfile user = token == null ? null : sessions.get(token);
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in.");
        return user;
    }

    /** The logged-in user if they have at least {@code role}, otherwise 401 or 403. */
    public UserProfile requireRole(String authorization, Role role) {
        UserProfile user = requireUser(authorization);
        if (!user.role().atLeast(role))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your account cannot do that.");
        return user;
    }

    private static String token(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return null;
        String token = authorization.substring(7).strip();
        return token.isEmpty() ? null : token;
    }

    private static String normalize(String username) {
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
