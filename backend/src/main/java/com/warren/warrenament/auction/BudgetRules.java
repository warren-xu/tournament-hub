package com.warren.warrenament.auction;

/** The arithmetic that decides whether a sealed bid is legal. */
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
     * Whether a team can still take part in a lot at all: it needs a free slot and enough
     * credits to meet the floor. Teams that cannot are not waited on when a lot is deciding
     * whether every captain has locked in.
     */
    public static boolean canBid(int remainingCredits, int rosterCount, int rosterSize, int minBid) {
        return maxBid(remainingCredits, rosterCount, rosterSize) >= minBid;
    }
}
