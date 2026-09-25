package com.warren.warrenament.profile;

import com.warren.warrenament.auth.AppUser;
import com.warren.warrenament.profile.PlayerProfileDtos.ProfileView;
import com.warren.warrenament.profile.PlayerProfileDtos.UpdateProfileRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/profiles")
public class PlayerProfileController {

    private final PlayerProfileService service;

    public PlayerProfileController(PlayerProfileService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProfileView> list() {
        return service.findAll();
    }

    @GetMapping("/me")
    public ProfileView me(@AuthenticationPrincipal AppUser user) {
        return service.findMine(user.userId());
    }

    @PutMapping("/me")
    public ProfileView updateMe(@AuthenticationPrincipal AppUser user,
                                @Valid @RequestBody UpdateProfileRequest request) {
        return service.updateMine(user.userId(), request);
    }

    @GetMapping("/{id}")
    public ProfileView byId(@PathVariable Long id) {
        return service.findById(id);
    }

    /** Hard delete, for clearing out test players before an event. Admins only. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @AuthenticationPrincipal AppUser user) {
        service.deletePlayer(id, user.userId());
        return ResponseEntity.noContent().build();
    }
}
