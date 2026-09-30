package com.warren.warrenament.auction;

/** The arithmetic that decides whether a bid is legal. */
public final class BudgetRules {

    private BudgetRules() {
    }

    /**
     * The most a team may bid: everything it has left.
     * <p>
     * There is deliberately no reserve held back for the slots still to fill. A captain who
     * empties the budget on one star is allowed to - they simply take whoever is left when
     * the random fill deals out the remaining players at the end.
     *
     * @return the ceiling, or 0 once the roster is full
     */
    public static int maxBid(int remainingCredits, int rosterCount, int rosterSize) {
        return rosterCount >= rosterSize ? 0 : Math.max(0, remainingCredits);
    }

    /**
     * Whether a team could still make a bid of {@code minBid}: it needs a free slot and the
     * credits for it (never less than one). A lot nobody else could bid on closes at once.
     */
    public static boolean canBid(int remainingCredits, int rosterCount, int rosterSize, int minBid) {
        return rosterCount < rosterSize && remainingCredits >= Math.max(1, minBid);
    }
}
