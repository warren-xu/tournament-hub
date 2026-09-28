/**
 * Ranks the players a captain could still draft, for their team specifically.
 *
 * Rank comes first: the base score is Riot's tier number (Iron 1 = 3 ... Radiant = 27), so
 * one division is one point. Role fit comes second: covering a role the team still lacks
 * is worth ROLE_BONUS points on the player's primary role, and a fraction of that on their
 * secondary. Because the bonus is only about one rank's worth, a large skill gap always
 * wins (a Diamond duelist over a Bronze initiator), while between players of similar rank
 * the one who completes the comp comes out ahead.
 */

/** Every team should field at least one of each. */
export const CORE_ROLES = ["Duelist", "Controller", "Initiator", "Sentinel"] as const;

/** Points for filling a missing role with a primary role: about one full rank. */
export const ROLE_BONUS = 3;
/** A secondary role is a weaker promise than a primary. */
export const SECONDARY_WEIGHT = 0.6;
/** Role need can at most double the bonus, however few slots are left. */
const MAX_URGENCY = 2;
/** Riot's own tier for "Unrated": unranked players sit below Iron 1 (3). */
export const UNRANKED_TIER = 0;

export interface DraftPlayer {
  profileId: number;
  username: string;
  currentRank: string | null;
  primaryRole: string | null;
  secondaryRole: string | null;
}

export interface Advice {
  player: DraftPlayer;
  score: number;
  /** The tier the score used; UNRANKED_TIER when the player has no rank. */
  tier: number;
  /** The core role this player would cover, if the team lacks it. */
  fills: string | null;
  fillsWith: "primary" | "secondary" | null;
}

const norm = (role: string | null) => role?.trim().toLowerCase() ?? "";

/** How far each core role is covered: 1 by a primary, SECONDARY_WEIGHT by a secondary. */
export function roleCoverage(roster: DraftPlayer[]): Map<string, number> {
  const coverage = new Map<string, number>(CORE_ROLES.map((r) => [r, 0]));
  for (const role of CORE_ROLES) {
    for (const p of roster) {
      const c = norm(p.primaryRole) === role.toLowerCase() ? 1
        : norm(p.secondaryRole) === role.toLowerCase() ? SECONDARY_WEIGHT : 0;
      coverage.set(role, Math.min(1, (coverage.get(role) ?? 0) + c));
    }
  }
  return coverage;
}

/**
 * Best pick first. `tierOf` maps a rank name to Riot's tier, or null if unknown.
 * `slotsLeft` is how many more players the team can take.
 */
export function rankCandidates(
  candidates: DraftPlayer[],
  roster: DraftPlayer[],
  slotsLeft: number,
  tierOf: (rank: string | null) => number | null,
): Advice[] {
  if (slotsLeft <= 0) return [];

  const coverage = roleCoverage(roster);
  const missing = CORE_ROLES.filter((r) => (coverage.get(r) ?? 0) < 1);
  // Four roles missing with two slots left is more pressing than one missing with four.
  const urgency = Math.min(MAX_URGENCY, Math.max(1, missing.length / slotsLeft));

  return candidates
    .map((player) => {
      const tier = tierOf(player.currentRank) ?? UNRANKED_TIER;

      let best = 0;
      let fills: string | null = null;
      let fillsWith: Advice["fillsWith"] = null;
      for (const role of missing) {
        const need = 1 - (coverage.get(role) ?? 0);
        const primary = norm(player.primaryRole) === role.toLowerCase() ? ROLE_BONUS * need : 0;
        const secondary = norm(player.secondaryRole) === role.toLowerCase() ? ROLE_BONUS * SECONDARY_WEIGHT * need : 0;
        const bonus = Math.max(primary, secondary) * urgency;
        if (bonus > best) {
          best = bonus;
          fills = role;
          fillsWith = primary >= secondary ? "primary" : "secondary";
        }
      }

      return { player, score: tier + best, tier, fills, fillsWith };
    })
    // Equal scores go to the higher-ranked player: skill is the surer bet.
    .sort((a, b) => b.score - a.score || b.tier - a.tier);
}
