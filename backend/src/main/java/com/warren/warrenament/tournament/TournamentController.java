package com.warren.warrenament.tournament;

import com.warren.warrenament.auth.AppUser;
import com.warren.warrenament.tournament.TournamentDtos.CreateTournamentRequest;
import com.warren.warrenament.tournament.TournamentDtos.QueuePlayersRequest;
import com.warren.warrenament.tournament.TournamentDtos.RegistrationView;
import com.warren.warrenament.tournament.TournamentDtos.TournamentView;
import com.warren.warrenament.tournament.TournamentDtos.UpdateScheduleRequest;
import com.warren.warrenament.tournament.TournamentDtos.UpdateStatusRequest;
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
@RequestMapping("/api/tournaments")
public class TournamentController {

    private final TournamentService service;

    public TournamentController(TournamentService service) {
        this.service = service;
    }

    @GetMapping
    public List<TournamentView> list() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public TournamentView byId(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping("/slug/{slug}")
    public TournamentView bySlug(@PathVariable String slug) {
        return service.findBySlug(slug);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public TournamentView create(@Valid @RequestBody CreateTournamentRequest request,
                                 @AuthenticationPrincipal AppUser user) {
        return service.create(request, user.userId());
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public TournamentView updateStatus(@PathVariable Long id,
                                       @RequestBody UpdateStatusRequest request) {
        return service.updateStatus(id, request.status());
    }

    @PutMapping("/{id}/schedule")
    @PreAuthorize("hasRole('ADMIN')")
    public TournamentView updateSchedule(@PathVariable Long id,
                                         @RequestBody UpdateScheduleRequest request) {
        return service.updateSchedule(id, request.startsAt());
    }

    /** Hard delete, taking teams, registrations and the auction with it. Admins only. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/registrations")
    public List<RegistrationView> registrations(@PathVariable Long id) {
        return service.registrations(id);
    }

    @PostMapping("/{id}/registrations")
    public RegistrationView register(@PathVariable Long id,
                                     @AuthenticationPrincipal AppUser user) {
        return service.register(id, user.userId());
    }

    /** Leave the pool before the draft starts. */
    @DeleteMapping("/{id}/registrations/me")
    public ResponseEntity<Void> withdraw(@PathVariable Long id,
                                         @AuthenticationPrincipal AppUser user) {
        service.withdraw(id, user.userId());
        return ResponseEntity.noContent().build();
    }

    /** Shuffle chosen players from the pool into the queue. Before the draft starts. */
    @PostMapping("/{id}/queue")
    @PreAuthorize("hasRole('ADMIN')")
    public List<RegistrationView> queuePlayers(@PathVariable Long id,
                                               @Valid @RequestBody QueuePlayersRequest request) {
        return service.queuePlayers(id, request.playerProfileIds());
    }

    @DeleteMapping("/{id}/queue/{profileId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeFromQueue(@PathVariable Long id, @PathVariable Long profileId) {
        service.removeFromQueue(id, profileId);
        return ResponseEntity.noContent().build();
    }
}
