package com.warren.warrenament.auction;

import com.warren.warrenament.tournament.Tournament;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "auctions")
@Getter
@Setter
@NoArgsConstructor
public class Auction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "tournament_id", nullable = false, unique = true)
    private Tournament tournament;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuctionStatus status = AuctionStatus.SETUP;

    @Column(name = "current_lot_id")
    private Long currentLotId;

    /** The team whose captain nominates next; teams take turns in creation order. */
    @Column(name = "turn_team_id")
    private Long turnTeamId;

    /** The lot that captain has picked, waiting for the admin to open bidding on it. */
    @Column(name = "pick_lot_id")
    private Long pickLotId;

    /** How long a nominated player stays open before the first bid; each bid then keeps it open a little longer. */
    @Column(name = "lot_duration_seconds", nullable = false)
    private int lotDurationSeconds = 30;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    public Auction(Tournament tournament) {
        this.tournament = tournament;
    }
}
