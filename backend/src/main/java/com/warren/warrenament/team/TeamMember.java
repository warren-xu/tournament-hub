package com.warren.warrenament.team;

import com.warren.warrenament.profile.PlayerProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "team_members")
@Getter
@Setter
@NoArgsConstructor
public class TeamMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Denormalised from the team so the unique constraint can span the whole tournament. */
    @Column(name = "tournament_id", nullable = false)
    private Long tournamentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "player_profile_id", nullable = false)
    private PlayerProfile playerProfile;

    @Column(name = "price_paid", nullable = false)
    private int pricePaid;

    @Column(name = "acquired_at", nullable = false, updatable = false, insertable = false)
    private Instant acquiredAt;

    public TeamMember(Team team, PlayerProfile playerProfile, int pricePaid) {
        this.tournamentId = team.getTournament().getId();
        this.team = team;
        this.playerProfile = playerProfile;
        this.pricePaid = pricePaid;
    }
}
