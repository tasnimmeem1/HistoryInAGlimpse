package com.historyinaglimpse.animation_backend.auth;

import com.historyinaglimpse.animation_backend.auth.UserAccount.UserProfile;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Account management for admins. Every endpoint answers 401 or 403 to everyone else. */
@RestController
@RequestMapping("/api/admin/users")
public class AdminController {
    private final AuthService auth;
    public AdminController(AuthService auth) { this.auth = auth; }

    public record RoleRequest(Role role) { }

    @GetMapping
    public List<UserProfile> users(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return auth.listUsers(authorization);
    }

    @PutMapping("/{username}/role")
    public UserProfile setRole(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                               @PathVariable("username") String username, @RequestBody RoleRequest body) {
        return auth.setRole(authorization, username, body.role());
    }
}
