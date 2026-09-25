package com.warren.warrenament.playercard;

public record PlayerCardView(Long id, String name, String smallArt, String largeArt) {
    public static PlayerCardView of(PlayerCard card) {
        return new PlayerCardView(card.getId(), card.getName(), card.getSmallArt(),
                card.getLargeArt());
    }
}
