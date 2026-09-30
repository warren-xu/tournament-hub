package com.warren.warrenament.team;

import com.warren.warrenament.auth.AppUser;
import com.warren.warrenament.team.TeamDtos.AssignCaptainRequest;
import com.warren.warrenament.team.TeamDtos.CreateTeamRequest;
import com.warren.warrenament.team.TeamDtos.ReorderTeamsRequest;
import com.warren.warrenament.team.TeamDtos.TeamView;
import com.warren.warrenament.team.TeamDtos.UpdateTeamRequest;
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
@RequestMapping("/api")
public class TeamController {

    private final TeamService service;

    public TeamController(TeamService service) {
        this.service = service;
    }

    @GetMapping("/tournaments/{tournamentId}/teams")
    public List<TeamView> byTournament(@PathVariable Long tournamentId) {
        return service.findByTournament(tournamentId);
    }

    @PostMapping("/tournaments/{tournamentId}/teams")
    public TeamView create(@PathVariable Long tournamentId,
                           @Valid @RequestBody CreateTeamRequest request,
                           @AuthenticationPrincipal AppUser user) {
        return service.create(tournamentId, request, user.userId(), user.isAdmin());
    }

    /** Admins only, before the draft starts: the order teams nominate in, first to last. */
    @PutMapping("/tournaments/{tournamentId}/teams/order")
    @PreAuthorize("hasRole('ADMIN')")
    public List<TeamView> reorder(@PathVariable Long tournamentId,
                                  @Valid @RequestBody ReorderTeamsRequest request) {
        return service.reorder(tournamentId, request.teamIds());
    }

    @GetMapping("/teams/{teamId}")
    public TeamView byId(@PathVariable Long teamId) {
        return service.findById(teamId);
    }

    @PutMapping("/teams/{teamId}")
    public TeamView update(@PathVariable Long teamId,
                           @Valid @RequestBody UpdateTeamRequest request,
                           @AuthenticationPrincipal AppUser user) {
        return service.update(teamId, request, user.userId(), user.isAdmin());
    }

    @PutMapping("/teams/{teamId}/captain")
    @PreAuthorize("hasRole('ADMIN')")
    public TeamView assignCaptain(@PathVariable Long teamId,
                                  @Valid @RequestBody AssignCaptainRequest request) {
        return service.assignCaptain(teamId, request.captainUserId());
    }

    /** Admins only. Before the draft starts; the captain's seat goes with the team. */
    @DeleteMapping("/teams/{teamId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long teamId,
                                       @AuthenticationPrincipal AppUser user) {
        service.delete(teamId, user.userId(), user.isAdmin());
        return ResponseEntity.noContent().build();
    }
}
