package com.warren.warrenament.profile;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import com.warren.warrenament.playercard.PlayerCardView;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

public final class PlayerProfileDtos {

    private PlayerProfileDtos() {
    }

    public record ProfileView(
            Long id,
            Long userId,
            String username,
            String avatarUrl,
            String riotId,
            String currentRank,
            String primaryRole,
            String secondaryRole,
            String bio,
            Set<String> agents,
            String mainAgent,
            PlayerCardView playerCard,
            String peakRank,
            String bannerUrl,
            Integer accentColor,
            Instant updatedAt
    ) {
        public static ProfileView of(PlayerProfile p) {
            return new ProfileView(
                    p.getId(),
                    p.getUser().getId(),
                    p.getUser().getUsername(),
                    p.getUser().getAvatarUrl(),
                    p.getRiotId(),
                    p.getCurrentRank(),
                    p.getPrimaryRole(),
                    p.getSecondaryRole(),
                    p.getBio(),
                    new LinkedHashSet<>(p.getAgents()),
                    p.getMainAgent(),
                    p.getPlayerCard() == null ? null : PlayerCardView.of(p.getPlayerCard()),
                    p.getPeakRank(),
                    p.getUser().getBannerUrl(),
                    p.getUser().getAccentColor(),
                    p.getUpdatedAt());
        }
    }

    public record UpdateProfileRequest(
            @Size(max = 64) String riotId,
            @Size(max = 32) String currentRank,
            @Size(max = 32) String primaryRole,
            @Size(max = 32) String secondaryRole,
            @Size(max = 1000) String bio,
            Set<String> agents,
            @Size(max = 64) String mainAgent,
            @Positive Long playerCardId
    ) {
    }
}
