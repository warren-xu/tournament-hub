package com.warren.warrenament.auction;

public enum LotStatus {
    /** Queued, not yet nominated. */
    PENDING,
    /** Nominated and accepting bids until {@code endsAt}. */
    OPEN,
    SOLD,
    /** Closed with no bids. Can be re-queued by the admin. */
    UNSOLD
}
