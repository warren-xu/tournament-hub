package com.warren.warrenament.profile;

/**
 * An admin-assigned handicap for strong players, played by the honour system and shown in
 * the draft room. Both tiers cap guns at the Warden (no Phantom or Vandal); Tier 1 also
 * limits them to Viper or Sage, so the auction treats them as a Sentinel with Controller
 * second. Their profile itself is left as they set it.
 */
public enum NerfTier {
    TIER_1,
    TIER_2;

    public static final String TIER_1_PRIMARY_ROLE = "Sentinel";
    public static final String TIER_1_SECONDARY_ROLE = "Controller";

    public boolean limitsAgents() {
        return this == TIER_1;
    }
}
