package com.warren.warrenament.auction;

public enum AuctionStatus {
    /** Lots being prepared; no bidding yet. */
    SETUP,
    LIVE,
    /** Admin pressed pause: timers stop advancing and bids are refused. */
    PAUSED,
    COMPLETE
}
