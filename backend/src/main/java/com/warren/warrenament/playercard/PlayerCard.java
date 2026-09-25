package com.warren.warrenament.playercard;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "player_cards")
@Getter
@Setter
@NoArgsConstructor
public class PlayerCard {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, unique = true)
    private String externalId;

    @Column(nullable = false)
    private String name;

    @Column(name = "small_art", length = 512)
    private String smallArt;

    @Column(name = "large_art", length = 512)
    private String largeArt;
}
