package com.historyinaglimpse.animation_backend.auth;

import com.historyinaglimpse.animation_backend.auth.AuthController.ProfileRequest;
import com.historyinaglimpse.animation_backend.auth.UserAccount.UserProfile;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profiles")
public class ProfileController {
    private final AuthService auth;

    public ProfileController(AuthService auth) {
        this.auth = auth;
    }

    @GetMapping("/me")
    public UserProfile me(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return auth.requireUser(authorization);
    }

    @PutMapping("/me")
    public UserProfile updateMe(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody ProfileRequest body) {
        return auth.updateProfile(authorization, body.displayName());
    }
}
