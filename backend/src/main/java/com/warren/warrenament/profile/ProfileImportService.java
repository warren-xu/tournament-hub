package com.warren.warrenament.profile;

import com.warren.warrenament.agent.Agent;
import com.warren.warrenament.agent.AgentRepository;
import com.warren.warrenament.common.Exceptions.BadRequestException;
import com.warren.warrenament.profile.MatchHistory.Account;
import com.warren.warrenament.profile.MatchHistory.Match;
import com.warren.warrenament.profile.MatchHistory.MatchPlayer;
import com.warren.warrenament.profile.MatchHistory.Rating;
import com.warren.warrenament.playercard.PlayerCard;
import com.warren.warrenament.playercard.PlayerCardRepository;
import com.warren.warrenament.rank.Rank;
import com.warren.warrenament.rank.RankRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Suggests profile fields from a Riot ID. Nothing is saved: the form fills in and the
 * player reviews it before pressing save.
 */
@Service
public class ProfileImportService {

    /** How many recent competitive matches decide the main agent and roles. */
    static final int HISTORY_SIZE = 10;

    /** Riot tiers 0-2 are Unrated/Unused; ranks start at Iron 1 = 3. */
    private static final int FIRST_RANKED_TIER = 3;

    public record ImportRequest(@NotBlank @Size(max = 64) String riotId) {
    }

    /** {@code playerCardId} is our own id for the equipped card, or null if it isn't synced. */
    public record ImportView(
            String riotId,
            String currentRank,
            String peakRank,
            Long playerCardId,
            String mainAgent,
            List<String> agents,
            String primaryRole,
            String secondaryRole,
            int matchesAnalyzed
    ) {
    }

    private final MatchHistory history;
    private final AgentRepository agents;
    private final RankRepository ranks;
    private final PlayerCardRepository playerCards;

    public ProfileImportService(MatchHistory history, AgentRepository agents, RankRepository ranks,
                                PlayerCardRepository playerCards) {
        this.history = history;
        this.agents = agents;
        this.ranks = ranks;
        this.playerCards = playerCards;
    }

    // Deliberately not @Transactional: it makes several slow HTTP calls, and holding one
    // of the few pooled database connections across them would starve everyone else.
    public ImportView importFromRiotId(ImportRequest request) {
        String[] nameAndTag = split(request.riotId());
        Account account = history.account(nameAndTag[0], nameAndTag[1])
                .orElseThrow(() -> new BadRequestException(
                        "Couldn't find that Riot ID. Check the spelling and the tag after the #."));

        // Competitive only: a Deathmatch warm-up says little about what someone plays in a
        // team. The queue is checked again here in case the API ignores the mode filter.
        List<MatchPlayer> games = history
                .recentCompetitiveMatches(account.affinity(), account.puuid(), HISTORY_SIZE).stream()
                .filter(m -> m.queue() == null || m.isCompetitive())
                .map(m -> self(m, account))
                .flatMap(Optional::stream)
                .toList();

        Optional<Rating> rating = history.rating(account.affinity(), account.puuid());
        Integer current = rating.map(Rating::current)
                .or(() -> games.stream().findFirst().map(MatchPlayer::tier))
                .orElse(null);
        Integer peak = rating.map(Rating::peak).orElse(null);

        Long cardId = Optional.ofNullable(account.cardId())
                .flatMap(playerCards::findByExternalId)
                .map(PlayerCard::getId)
                .orElse(null);

        List<Agent> picks = games.stream()
                .map(g -> Optional.ofNullable(g.agentName()).flatMap(agents::findByNameIgnoreCase))
                .flatMap(Optional::stream)
                .toList();
        List<String> pool = byFrequency(picks, Agent::getName);
        List<String> roles = byFrequency(picks, Agent::getRole);
        return new ImportView(
                account.riotId(), rankName(current), rankName(peak), cardId,
                pool.isEmpty() ? null : pool.getFirst(),
                pool,
                roles.isEmpty() ? null : roles.getFirst(),
                roles.size() < 2 ? null : roles.get(1),
                games.size());
    }

    /** "Name#TAG" to name and tag. People type spaces around the #; names may contain spaces. */
    static String[] split(String riotId) {
        String text = riotId == null ? "" : riotId.trim();
        int hash = text.lastIndexOf('#');
        String name = hash < 0 ? "" : text.substring(0, hash).trim();
        String tag = hash < 0 ? "" : text.substring(hash + 1).trim();
        if (name.isEmpty() || tag.isEmpty()) {
            throw new BadRequestException("Enter your full Riot ID, including the tag: name#tag.");
        }
        return new String[]{name, tag};
    }

    /** Our rank name for one of Riot's tiers; unranked tiers (below Iron 1) have none. */
    private String rankName(Integer tier) {
        return tier == null || tier < FIRST_RANKED_TIER
                ? null
                : ranks.findByTier(tier).map(Rank::getName).orElse(null);
    }

    private static Optional<MatchPlayer> self(Match match, Account account) {
        return match.players().stream().filter(p -> p.puuid().equals(account.puuid())).findFirst();
    }

    /** Distinct values, most common first; ties keep the order they were first seen. */
    private static <T> List<String> byFrequency(List<T> items, Function<T, String> key) {
        Map<String, Long> counts = items.stream()
                .collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.counting()));
        Map<String, Integer> firstSeen = new HashMap<>();
        counts.keySet().forEach(k -> firstSeen.put(k, firstSeen.size()));
        return counts.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<String, Long> e) -> -e.getValue())
                        .thenComparing(e -> firstSeen.get(e.getKey())))
                .map(Map.Entry::getKey)
                .toList();
    }
}
