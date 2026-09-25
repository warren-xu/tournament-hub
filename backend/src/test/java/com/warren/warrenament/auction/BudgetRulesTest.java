package com.warren.warrenament.auction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetRulesTest {

    @Test
    @DisplayName("a captain may bid the entire budget on one player")
    void noReserveIsHeldBackForEmptySlots() {
        // Under sealed bidding there is no reserve: spending it all is a legal gamble that
        // trades away any say in who fills the other four slots.
        assertThat(BudgetRules.maxBid(100, 0, 5)).isEqualTo(100);
    }

    @ParameterizedTest(name = "credits={0} roster={1}/{2} -> maxBid={3}")
    @CsvSource({
            "100, 0, 5, 100",
            "100, 4, 5, 100",   // last slot, same ceiling
            "100, 5, 5, 0",     // roster full
            "100, 6, 5, 0",     // over-full, still clamped to zero
            "10,  0, 5, 10",
            "0,   2, 5, 0",     // broke, and waiting on the random fill
    })
    void maxBidTable(int credits, int roster, int size, int expected) {
        assertThat(BudgetRules.maxBid(credits, roster, size)).isEqualTo(expected);
    }

    @Test
    @DisplayName("a team is only waited on while it can still meet the floor")
    void canBidNeedsASlotAndTheFloor() {
        assertThat(BudgetRules.canBid(5, 0, 5, 5)).isTrue();
        assertThat(BudgetRules.canBid(4, 0, 5, 5)).isFalse();   // cannot reach the floor
        assertThat(BudgetRules.canBid(100, 5, 5, 1)).isFalse(); // no slot to fill
        assertThat(BudgetRules.canBid(0, 0, 5, 1)).isFalse();   // broke
    }

    @Test
    void maxBidIsNeverNegative() {
        assertThat(BudgetRules.maxBid(-3, 0, 5)).isZero();
    }
}
