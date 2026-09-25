package com.warren.warrenament.rank;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class RankDtos {

    private RankDtos() {
    }

    public record RankView(
            Long id,
            int tier,
            String name,
            String division,
            String color,
            String iconUrl,
            boolean active
    ) {
        public static RankView of(Rank r) {
            return new RankView(r.getId(), r.getTier(), r.getName(), r.getDivision(),
                    r.getColor(), r.getIconUrl(), r.isActive());
        }
    }

    public record UpdateRankRequest(
            @NotBlank @Size(max = 32) String name,
            Boolean active
    ) {
    }
}
