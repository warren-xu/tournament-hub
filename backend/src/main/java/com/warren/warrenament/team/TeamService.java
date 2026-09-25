package com.warren.warrenament.team;

import com.warren.warrenament.auth.UserRepository;
import com.warren.warrenament.common.Exceptions.BadRequestException;
import com.warren.warrenament.common.Exceptions.ForbiddenException;
import com.warren.warrenament.common.Exceptions.NotFoundException;
import com.warren.warrenament.team.TeamDtos.CreateTeamRequest;
import com.warren.warrenament.team.TeamDtos.TeamView;
import com.warren.warrenament.team.TeamDtos.UpdateTeamRequest;
import com.warren.warrenament.tournament.Tournament;
import com.warren.warrenament.tournament.TournamentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class TeamService {

    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final TournamentRepository tournaments;
    private final UserRepository users;

    public TeamService(TeamRepository teams,
                       TeamMemberRepository members,
                       TournamentRepository tournaments,
                       UserRepository users) {
        this.teams = teams;
        this.members = members;
        this.tournaments = tournaments;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<TeamView> findByTournament(Long tournamentId) {
        return teams.findByTournamentId(tournamentId).stream()
                .map(team -> TeamView.of(team, members.findByTeamId(team.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public TeamView findById(Long teamId) {
        Team team = require(teamId);
        return TeamView.of(team, members.findByTeamId(teamId));
    }

    @Transactional
    public TeamView create(Long tournamentId, CreateTeamRequest request, Long callerUserId, boolean callerIsAdmin) {
        Tournament tournament = tournaments.findById(tournamentId)
                .orElseThrow(() -> NotFoundException.of("Tournament", tournamentId));

        Long captainUserId = Objects.requireNonNullElse(request.captainUserId(), callerUserId);
        if (!captainUserId.equals(callerUserId) && !callerIsAdmin) {
            throw new ForbiddenException("Only an admin can create a team for someone else");
        }
        if (users.findById(captainUserId).isEmpty()) {
            throw NotFoundException.of("User", captainUserId);
        }
        if (teams.existsByTournamentIdAndCaptainUserId(tournamentId, captainUserId)) {
            throw new BadRequestException("That captain already has a team in this tournament");
        }

        Team team = new Team(tournament, request.name(), request.logoUrl(), captainUserId);
        return TeamView.of(teams.save(team), List.of());
    }

    @Transactional
    public TeamView update(Long teamId, UpdateTeamRequest request, Long callerUserId, boolean callerIsAdmin) {
        Team team = require(teamId);
        requireCaptainOrAdmin(team, callerUserId, callerIsAdmin);
        team.setName(request.name());
        team.setLogoUrl(request.logoUrl());
        return TeamView.of(teams.save(team), members.findByTeamId(teamId));
    }

    @Transactional
    public TeamView assignCaptain(Long teamId, Long captainUserId) {
        Team team = require(teamId);
        if (users.findById(captainUserId).isEmpty()) {
            throw NotFoundException.of("User", captainUserId);
        }
        Long tournamentId = team.getTournament().getId();
        teams.findByTournamentIdAndCaptainUserId(tournamentId, captainUserId).ifPresent(existing -> {
            if (!existing.getId().equals(teamId)) {
                throw new BadRequestException("That user already captains another team here");
            }
        });
        team.setCaptainUserId(captainUserId);
        return TeamView.of(teams.save(team), members.findByTeamId(teamId));
    }

    @Transactional
    public void delete(Long teamId, Long callerUserId, boolean callerIsAdmin) {
        Team team = require(teamId);
        requireCaptainOrAdmin(team, callerUserId, callerIsAdmin);
        if (!members.findByTeamId(teamId).isEmpty()) {
            throw new BadRequestException("Cannot delete a team that already has players drafted");
        }
        teams.delete(team);
    }

    @Transactional(readOnly = true)
    public boolean isCaptain(Long tournamentId, Long userId) {
        return teams.existsByTournamentIdAndCaptainUserId(tournamentId, userId);
    }

    private void requireCaptainOrAdmin(Team team, Long callerUserId, boolean callerIsAdmin) {
        if (!callerIsAdmin && !team.getCaptainUserId().equals(callerUserId)) {
            throw new ForbiddenException("Only this team's captain or an admin can change it");
        }
    }

    private Team require(Long teamId) {
        return teams.findById(teamId).orElseThrow(() -> NotFoundException.of("Team", teamId));
    }
}
