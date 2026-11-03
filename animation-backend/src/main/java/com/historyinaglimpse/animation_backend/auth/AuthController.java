package com.historyinaglimpse.animation_backend.auth;

import com.historyinaglimpse.animation_backend.auth.UserAccount.UserProfile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }

    public record RegisterRequest(String username, String displayName, String password) { }
    public record LoginRequest(String username, String password) { }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserProfile register(@RequestBody RegisterRequest body) {
        return auth.register(body.username(), body.displayName(), body.password());
    }

    @PostMapping("/login")
    public AuthService.Login login(@RequestBody LoginRequest body) {
        return auth.login(body.username(), body.password());
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
}
