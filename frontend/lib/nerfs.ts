import type { NerfTier, ProfileView } from "./types";

const GUN_LIMIT = "Warden max, no Phantom or Vandal";

/** What each tier means, as the draft room lists it. */
export const NERFS: Record<NerfTier, { label: string; rules: string[] }> = {
  TIER_1: { label: "Tier 1", rules: ["Viper or Sage only", GUN_LIMIT] },
  TIER_2: { label: "Tier 2", rules: [GUN_LIMIT] },
};

/** Badge colour per tier, as Tailwind classes: gold for Tier 1, silver for Tier 2. */
export const TIER_COLOR: Record<NerfTier, string> = {
  TIER_1: "border-prestige text-prestige",
  TIER_2: "border-prestige-silver text-prestige-silver",
};

/** The short form on a pool tile. */
export const TIER_SHORT: Record<NerfTier, string> = { TIER_1: "T1", TIER_2: "T2" };

export function limitsAgents(tier: NerfTier | null | undefined): boolean {
  return tier === "TIER_1";
}

/** How the auction sees a player: Tier 1 plays Viper or Sage, so Sentinel with Controller second. */
export function asAuctionProfile(profile: ProfileView): ProfileView {
  return limitsAgents(profile.nerfTier)
    ? { ...profile, primaryRole: "Sentinel", secondaryRole: "Controller" }
    : profile;
}
