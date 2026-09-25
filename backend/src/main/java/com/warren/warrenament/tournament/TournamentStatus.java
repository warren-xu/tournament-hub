package com.warren.warrenament.tournament;

public enum TournamentStatus {
    /** Being set up; registration not yet open. */
    DRAFT,
    /** Players can register. */
    REGISTRATION,
    /** Registration closed, auction running or about to. */
    DRAFTING,
    /** Rosters locked. */
    LIVE,
    COMPLETE
}
