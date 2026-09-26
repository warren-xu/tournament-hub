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
import com.warren.warrenament.profile.ProfileImportService.ImportRequest;
import com.warren.warrenament.profile.ProfileImportService.ImportView;
import com.warren.warrenament.rank.Rank;
import com.warren.warrenament.rank.RankRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProfileImportServiceTest {

    private static final Account ME = new Account("me-puuid", "Boom Bot", "NA1", "na", "card-uuid");

    /** A fake HenrikDev: one known account plus a canned history. */
    private static final class FakeHistory implements MatchHistory {
        String lookedUpName;
        String lookedUpTag;
        Optional<Rating> rating = Optional.empty();
        List<Match> recent = List.of();

        @Override public Optional<Account> account(String name, String tag) {
            lookedUpName = name;
            lookedUpTag = tag;
            return name.equalsIgnoreCase(ME.name()) && tag.equalsIgnoreCase(ME.tag())
                    ? Optional.of(ME) : Optional.empty();
        }
        @Override public Optional<Rating> rating(String affinity, String puuid) { return rating; }
        @Override public List<Match> recentCompetitiveMatches(String affinity, String puuid, int count) {
            return recent;
        }
    }

    private final FakeHistory history = new FakeHistory();
    private final AgentRepository agents = mock(AgentRepository.class);
    private final RankRepository ranks = mock(RankRepository.class);
    private final PlayerCardRepository playerCards = mock(PlayerCardRepository.class);
    private final ProfileImportService service =
            new ProfileImportService(history, agents, ranks, playerCards);

    @BeforeEach
    void roster() {
        when(playerCards.findByExternalId(anyString())).thenReturn(Optional.empty());
        when(agents.findByNameIgnoreCase(anyString()))
                .thenAnswer(inv -> switch (((String) inv.getArgument(0)).toLowerCase()) {
                    case "jett" -> Optional.of(new Agent("Jett", "Duelist", 10));
                    case "raze" -> Optional.of(new Agent("Raze", "Duelist", 20));
                    case "omen" -> Optional.of(new Agent("Omen", "Controller", 10));
                    case "sova" -> Optional.of(new Agent("Sova", "Initiator", 10));
                    default -> Optional.empty();
                });
        when(ranks.findByTier(anyInt())).thenAnswer(inv -> {
            int tier = inv.getArgument(0);
            String name = switch (tier) {
                case 14 -> "Gold 3";
                case 17 -> "Diamond 1";
                case 21 -> "Ascendant 1";
                default -> "Tier " + tier;
            };
            return Optional.of(new Rank(tier, name, name.split(" ")[0]));
        });
    }

    /** A match this player took part in, alongside someone else. */
    private static Match game(String id, String queue, String agent, int tier) {
        return new Match(id, "na", queue, List.of(
                new MatchPlayer(ME.puuid(), agent, tier),
                new MatchPlayer("other-puuid", "Omen", 12)));
    }

    private ImportView fill(String riotId) {
        return service.importFromRiotId(new ImportRequest(riotId));
    }

    @Test
    @DisplayName("fills rank, main agent, pool and roles from recent competitive games")
    void suggestsFromHistory() {
        history.rating = Optional.of(new Rating(17, 21));
        history.recent = List.of(
                game("m1", "competitive", "Jett", 14),
                game("m2", "competitive", "Raze", 14),
                game("m3", "competitive", "Jett", 14),
                game("m4", "competitive", "Omen", 13));

        ImportView view = fill("Boom Bot#NA1");

        assertThat(view.riotId()).isEqualTo("Boom Bot#NA1");
        assertThat(view.currentRank()).isEqualTo("Diamond 1");     // live rank, not match-time
        assertThat(view.peakRank()).isEqualTo("Ascendant 1");
        assertThat(view.mainAgent()).isEqualTo("Jett");
        assertThat(view.agents()).containsExactly("Jett", "Raze", "Omen");
        assertThat(view.primaryRole()).isEqualTo("Duelist");
        assertThat(view.secondaryRole()).isEqualTo("Controller");
        assertThat(view.matchesAnalyzed()).isEqualTo(4);
    }

    @Test
    @DisplayName("returns the Riot ID as Riot spells it, whatever was typed")
    void canonicalRiotId() {
        ImportView view = fill("  boom bot #  na1 ");

        assertThat(history.lookedUpName).isEqualTo("boom bot");
        assertThat(history.lookedUpTag).isEqualTo("na1");
        assertThat(view.riotId()).isEqualTo("Boom Bot#NA1");
    }

    @Test
    @DisplayName("games from other modes are ignored even if the API lets them through")
    void competitiveOnly() {
        history.recent = List.of(
                game("m1", "competitive", "Omen", 14),
                game("m2", "unrated", "Jett", 14));

        ImportView view = fill("Boom Bot#NA1");

        assertThat(view.agents()).containsExactly("Omen");
        assertThat(view.primaryRole()).isEqualTo("Controller");
        assertThat(view.matchesAnalyzed()).isEqualTo(1);
    }

    @Test
    @DisplayName("without a live rank, uses the rank of the latest competitive game, and no peak")
    void rankFallsBackToLatestGame() {
        history.recent = List.of(game("m1", "competitive", "Sova", 14), game("m2", "competitive", "Sova", 12));

        ImportView view = fill("Boom Bot#NA1");
        assertThat(view.currentRank()).isEqualTo("Gold 3");
        assertThat(view.peakRank()).isNull();
    }

    @Test
    @DisplayName("equips the player card the account has on in game, when the catalog has it")
    void equipsTheInGameCard() {
        assertThat(fill("Boom Bot#NA1").playerCardId()).isNull();   // not synced yet

        PlayerCard card = mock(PlayerCard.class);
        when(card.getId()).thenReturn(42L);
        when(playerCards.findByExternalId("card-uuid")).thenReturn(Optional.of(card));

        assertThat(fill("Boom Bot#NA1").playerCardId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("with no competitive games, agents and roles stay blank")
    void noCompetitiveGames() {
        history.rating = Optional.of(new Rating(14, 14));

        ImportView view = fill("Boom Bot#NA1");

        assertThat(view.currentRank()).isEqualTo("Gold 3");
        assertThat(view.mainAgent()).isNull();
        assertThat(view.agents()).isEmpty();
        assertThat(view.primaryRole()).isNull();
        assertThat(view.matchesAnalyzed()).isZero();
    }

    @Test
    @DisplayName("an unranked player gets no rank rather than a wrong one")
    void unrankedLeavesRankEmpty() {
        history.rating = Optional.of(new Rating(0, null));

        ImportView view = fill("Boom Bot#NA1");
        assertThat(view.currentRank()).isNull();
        assertThat(view.peakRank()).isNull();
    }

    @Test
    @DisplayName("a Riot ID without a tag, or one that doesn't exist, is a clear error")
    void badRiotIds() {
        assertThatThrownBy(() -> fill("Boom Bot")).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("including the tag");
        assertThatThrownBy(() -> fill("Boom Bot#")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> fill("Nobody#0000")).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Couldn't find that Riot ID");
    }
}
