package com.warren.warrenament.rank;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "ranks")
@Getter
@Setter
@NoArgsConstructor
public class Rank {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Riot's numeric tier. Stable key and the correct ascending sort order. */
    @Column(nullable = false, unique = true)
    private int tier;

    /** Display name, title-cased from Riot's shouty "IRON 1". */
    @Column(nullable = false, unique = true)
    private String name;

    /** "Iron", "Bronze" — used to group the picker. */
    @Column(nullable = false)
    private String division;

    /** Riot's RGBA hex for the tier, e.g. {@code 868986ff}. */
    private String color;

    @Column(name = "icon_url")
    private String iconUrl;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    public Rank(int tier, String name, String division) {
        this.tier = tier;
        this.name = name;
        this.division = division;
    }
}
