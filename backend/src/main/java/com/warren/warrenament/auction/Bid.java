package com.warren.warrenament.auction;

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

/** Append-only. Never updated or deleted - it is the audit trail for the whole draft. */
@Entity
@Table(name = "bids")
@Getter
@Setter
@NoArgsConstructor
public class Bid {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lot_id", nullable = false)
    private Long lotId;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private int amount;

    /**
     * Set here rather than left to the column default: the bid is broadcast straight after
     * the insert, and a database default is not read back, so the ticker would show no time.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Bid(Long lotId, Long teamId, Long userId, int amount) {
        this.lotId = lotId;
        this.teamId = teamId;
        this.userId = userId;
        this.amount = amount;
        this.createdAt = Instant.now();
    }
}
