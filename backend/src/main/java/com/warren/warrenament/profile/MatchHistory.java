package com.warren.warrenament.profile;

import java.util.List;
import java.util.Optional;

/**
 * Account, match and rank lookups used to pre-fill a profile from a Riot ID. Kept behind an interface, like the
 * agent and rank catalogs, so the import logic is testable without the network.
 */
public interface MatchHistory {

    /**
     * A Riot account: its permanent id, the region ("affinity") its games are played in, and
     * the id of the player card it has equipped (Riot's uuid, as synced into player_cards).
     */
    record Account(String puuid, String name, String tag, String affinity, String cardId) {
        public String riotId() {
            return name + "#" + tag;
        }
    }

    /** One participant in a match. {@code tier} is Riot's numeric rank at match time. */
    record MatchPlayer(String puuid, String agentName, int tier) {
    }

    /**
     * {@code affinity} is the region the match was found in (na, eu, ap, ...); {@code queue}
     * is Riot's queue id, e.g. "competitive", or null when the API omits it.
     */
    record Match(String matchId, String affinity, String queue, List<MatchPlayer> players) {
        public boolean isCompetitive() {
            return "competitive".equalsIgnoreCase(queue);
        }
    }

    /**
     * Looks up a Riot ID (name and tagline, without the #); empty if no such account.
     * Throws a readable error when the account exists but has no recent game on record.
     */
    Optional<Account> account(String name, String tag);

    /** Riot's numeric tiers (Iron 1 = 3 ... Radiant = 27); either may be null. */
    record Rating(Integer current, Integer peak) {
    }

    /** The player's current and peak competitive tiers, if the lookup succeeds. */
    Optional<Rating> rating(String affinity, String puuid);

    /** The player's most recent competitive matches, newest first; empty if the lookup fails. */
    List<Match> recentCompetitiveMatches(String affinity, String puuid, int count);
}
