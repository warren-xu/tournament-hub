package com.warren.warrenament.tournament;

import com.warren.warrenament.profile.PlayerProfileDtos.ProfileView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;
import jakarta.validation.constraints.Size;

public final class TournamentDtos {

    private TournamentDtos() {
    }

    public record TournamentView(
            Long id,
            String name,
            String slug,
            TournamentStatus status,
            int creditBudget,
            int rosterSize,
            int minBid,
            Instant startsAt
    ) {
        public static TournamentView of(Tournament t) {
            return new TournamentView(t.getId(), t.getName(), t.getSlug(), t.getStatus(),
                    t.getCreditBudget(), t.getRosterSize(), t.getMinBid(), t.getStartsAt());
        }
    }

    public record CreateTournamentRequest(
            @NotBlank @Size(max = 128) String name,
            @Size(max = 128) String slug,
            @Min(1) @Max(100000) Integer creditBudget,
            @Min(1) @Max(10) Integer rosterSize,
            @Min(1) Integer minBid,
            Instant startsAt,
            @NotNull @Size(min = 2) List<@NotNull @Positive Long> captainProfileIds
    ) {
    }

    /** Players from the pool to shuffle into the queue. */
    public record QueuePlayersRequest(@NotNull @Size(min = 1) List<@NotNull @Positive Long> playerProfileIds) {
    }

    public record UpdateStatusRequest(TournamentStatus status) {
    }

    /** A null {@code startsAt} clears the date back to "to be announced". */
    public record UpdateScheduleRequest(Instant startsAt) {
    }

    public record RegistrationView(
            Long id,
            Long tournamentId,
            RegistrationStatus status,
            ProfileView player
    ) {
        public static RegistrationView of(Registration r) {
            return new RegistrationView(
                    r.getId(),
                    r.getTournament().getId(),
                    r.getStatus(),
                    ProfileView.of(r.getPlayerProfile()));
        }
    }
}
