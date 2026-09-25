package com.warren.warrenament.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MeController {

    public record MeView(Long userId, String username, String avatarUrl, Role role) {
    }

    /** Returns 204 when signed out so the frontend can branch without treating it as an error. */
    @GetMapping("/me")
    public ResponseEntity<MeView> me(@AuthenticationPrincipal AppUser user) {
        if (user == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(
                new MeView(user.userId(), user.username(), user.avatarUrl(), user.role()));
    }
}
