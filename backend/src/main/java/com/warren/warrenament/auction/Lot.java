package com.warren.warrenament.auction;

import com.warren.warrenament.profile.PlayerProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "lots")
@Getter
@Setter
@NoArgsConstructor
public class Lot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "auction_id", nullable = false)
    private Auction auction;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "player_profile_id", nullable = false)
    private PlayerProfile playerProfile;

    @Column(name = "seq", nullable = false)
    private int seq;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LotStatus status = LotStatus.PENDING;

    /** The current price while the lot is open (0 before any bid), then what it sold for. */
    @Column(name = "winning_bid", nullable = false)
    private int winningBid;

    @Column(name = "winning_team_id")
    private Long winningTeamId;

    @Column(name = "ends_at")
    private Instant endsAt;

    /**
     * When this lot was last put up. Bids older than this belong to a previous round - a
     * player who went unsold comes back through the queue - and don't count this time.
     */
    @Column(name = "opened_at")
    private Instant openedAt;

    /** Set while the auction is paused so the countdown resumes where it stopped. */
    @Column(name = "paused_remaining_ms")
    private Long pausedRemainingMs;

    /**
     * Incremented by Hibernate on every write. Doubles as the ordering token clients use to
     * discard stale broadcasts that arrive out of order.
     */
    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    public Lot(Auction auction, PlayerProfile playerProfile, int seq) {
        this.auction = auction;
        this.playerProfile = playerProfile;
        this.seq = seq;
    }
}
