import type { NerfTier, ProfileView } from "./types";

const GUN_LIMIT = "Warden max, no Phantom or Vandal";

/** What each tier means, as the draft room lists it. */
export const NERFS: Record<NerfTier, { label: string; rules: string[] }> = {
  TIER_1: { label: "Tier 1", rules: ["Viper or Sage only", GUN_LIMIT] },
  TIER_2: { label: "Tier 2", rules: [GUN_LIMIT] },
};

export function limitsAgents(tier: NerfTier | null | undefined): boolean {
  return tier === "TIER_1";
}

/** How the auction sees a player: Tier 1 plays Viper or Sage, so Sentinel with Controller second. */
export function asAuctionProfile(profile: ProfileView): ProfileView {
  return limitsAgents(profile.nerfTier)
    ? { ...profile, primaryRole: "Sentinel", secondaryRole: "Controller" }
    : profile;
}
