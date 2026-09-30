package com.warren.warrenament.profile;

import com.warren.warrenament.auth.AppUser;
import com.warren.warrenament.profile.PlayerProfileDtos.ProfileView;
import com.warren.warrenament.profile.PlayerProfileDtos.SetNerfTierRequest;
import com.warren.warrenament.profile.PlayerProfileDtos.UpdateProfileRequest;
import com.warren.warrenament.profile.ProfileImportService.ImportRequest;
import com.warren.warrenament.profile.ProfileImportService.ImportView;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/profiles")
public class PlayerProfileController {

    private final PlayerProfileService service;
    private final ProfileImportService importer;

    public PlayerProfileController(PlayerProfileService service, ProfileImportService importer) {
        this.service = service;
        this.importer = importer;
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

    /**
     * Suggests field values from a Riot ID's recent competitive games. Everything is left for
     * the player to review and save, except peak rank: that is only ever set from Riot's
     * data, so it's saved here and can't be edited in the profile form.
     */
    @PostMapping("/me/import")
    public ImportView importFromRiotId(@AuthenticationPrincipal AppUser user,
                                       @Valid @RequestBody ImportRequest request) {
        ImportView result = importer.importFromRiotId(request);
        if (result.peakRank() != null) {
            service.setPeakRank(user.userId(), result.peakRank());
        }
        return result;
    }

    /** Admins only: set a player's nerf tier, or clear it with {@code {"tier": null}}. */
    @PutMapping("/{id}/nerf")
    @PreAuthorize("hasRole('ADMIN')")
    public ProfileView setNerfTier(@PathVariable Long id, @RequestBody SetNerfTierRequest request) {
        return service.setNerfTier(id, request.tier());
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
