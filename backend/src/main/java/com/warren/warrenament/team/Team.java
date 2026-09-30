package com.warren.warrenament.team;

import com.warren.warrenament.tournament.Tournament;
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
@Table(name = "teams")
@Getter
@Setter
@NoArgsConstructor
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @Column(nullable = false)
    private String name;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "captain_user_id", nullable = false)
    private Long captainUserId;

    /** Turn order for nominating (1 goes first), and the order teams are listed in. */
    @Column(name = "draft_order", nullable = false)
    private int draftOrder;

    /** Decremented when a lot is won. The source of truth for what a captain can still spend. */
    @Column(name = "remaining_credits", nullable = false)
    private int remainingCredits;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    public Team(Tournament tournament, String name, String logoUrl, Long captainUserId) {
        this.tournament = tournament;
        this.name = name;
        this.logoUrl = logoUrl;
        this.captainUserId = captainUserId;
        this.remainingCredits = tournament.getCreditBudget();
    }
}
