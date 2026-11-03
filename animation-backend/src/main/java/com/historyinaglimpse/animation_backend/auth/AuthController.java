package com.historyinaglimpse.animation_backend.auth;

import com.historyinaglimpse.animation_backend.auth.UserAccount.UserProfile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final GoogleSignIn google;
    public AuthController(AuthService auth, GoogleSignIn google) { this.auth = auth; this.google = google; }

    public record RegisterRequest(String username, String displayName, String password) { }
    public record LoginRequest(String username, String password) { }
    public record ProfileRequest(String displayName) { }
    public record PasswordRequest(String currentPassword, String newPassword) { }
    public record GoogleConfig(boolean enabled, String clientId) { }
    public record GoogleRequest(String code, String codeVerifier, String redirectUri) { }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserProfile register(@RequestBody RegisterRequest body) {
        return auth.register(body.username(), body.displayName(), body.password());
    }

    @PostMapping("/login")
    public AuthService.Login login(@RequestBody LoginRequest body) {
        return auth.login(body.username(), body.password());
    }

    /** Tells the desktop app whether to show the Google button, and the id it needs. */
    @GetMapping("/google")
    public GoogleConfig googleConfig() {
        return google.enabled() ? new GoogleConfig(true, google.clientId()) : new GoogleConfig(false, null);
    }

    @PostMapping("/google")
    public AuthService.Login googleLogin(@RequestBody GoogleRequest body) {
        return auth.googleLogin(google.exchange(body.code(), body.codeVerifier(), body.redirectUri()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        auth.logout(authorization);
    }

    @GetMapping("/me")
    public UserProfile me(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return auth.requireUser(authorization);
    }

    @PutMapping("/me")
    public UserProfile updateMe(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                @RequestBody ProfileRequest body) {
        return auth.updateProfile(authorization, body.displayName());
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                               @RequestBody PasswordRequest body) {
        auth.changePassword(authorization, body.currentPassword(), body.newPassword());
    }
}
