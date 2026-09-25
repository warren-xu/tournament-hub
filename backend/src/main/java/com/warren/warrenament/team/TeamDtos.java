package com.warren.warrenament.team;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class TeamDtos {

    private TeamDtos() {
    }

    public record TeamMemberView(Long id, Long profileId, String username, int pricePaid) {
        public static TeamMemberView of(TeamMember m) {
            return new TeamMemberView(
                    m.getId(),
                    m.getPlayerProfile().getId(),
                    m.getPlayerProfile().getUser().getUsername(),
                    m.getPricePaid());
        }
    }

    public record TeamView(
            Long id,
            Long tournamentId,
            String name,
            String logoUrl,
            Long captainUserId,
            int remainingCredits,
            List<TeamMemberView> roster
    ) {
        public static TeamView of(Team team, List<TeamMember> members) {
            return new TeamView(
                    team.getId(),
                    team.getTournament().getId(),
                    team.getName(),
                    team.getLogoUrl(),
                    team.getCaptainUserId(),
                    team.getRemainingCredits(),
                    members.stream().map(TeamMemberView::of).toList());
        }
    }

    public record CreateTeamRequest(
            @NotBlank @Size(max = 128) String name,
            @Size(max = 512) String logoUrl,
            /* Omit to make yourself captain; admins can name someone else. */
            Long captainUserId
    ) {
    }

    public record UpdateTeamRequest(
            @NotBlank @Size(max = 128) String name,
            @Size(max = 512) String logoUrl
    ) {
    }

    public record AssignCaptainRequest(@NotNull Long captainUserId) {
    }
}
