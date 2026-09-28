package com.warren.warrenament.auction;

import com.warren.warrenament.TestFixtures;
import com.warren.warrenament.TestcontainersConfiguration;
import com.warren.warrenament.auction.AuctionDtos.AuctionSnapshot;
import com.warren.warrenament.auction.AuctionDtos.LotView;
import com.warren.warrenament.auth.User;
import com.warren.warrenament.common.Exceptions.BadRequestException;
import com.warren.warrenament.common.Exceptions.BidRejectedException;
import com.warren.warrenament.profile.PlayerProfile;
import com.warren.warrenament.team.Team;
import com.warren.warrenament.team.TeamMember;
import com.warren.warrenament.team.TeamMemberRepository;
import com.warren.warrenament.team.TeamRepository;
import com.warren.warrenament.tournament.Tournament;
import com.warren.warrenament.tournament.TournamentRepository;
import com.warren.warrenament.tournament.TournamentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AuctionEngineIntegrationTest {

    @Autowired TestFixtures fixtures;
    @Autowired BidService bidService;
    @Autowired AuctionService auctionService;
    @Autowired LotSweeper sweeper;
    @Autowired LotRepository lots;
    @Autowired BidRepository bids;
    @Autowired TeamRepository teams;
    @Autowired TeamMemberRepository teamMembers;
    @Autowired AuctionRepository auctions;
    @Autowired TournamentRepository tournaments;

    private record World(Tournament tournament, Auction auction, Lot lot, List<Team> teams,
                         List<User> captains) {

        Long auctionId() {
            return auction.getId();
        }

        Long lotId() {
            return lot.getId();
        }

        Long captain(int i) {
            return captains.get(i).getId();
        }

        Long teamId(int i) {
            return teams.get(i).getId();
        }
    }

    private World world(int teamCount, int budget, int rosterSize, int secondsRemaining) {
        Tournament tournament = fixtures.tournament(budget, rosterSize, 1);
        Auction auction = fixtures.liveAuction(tournament, 30);
        List<Team> teamList = new ArrayList<>();
        List<User> captains = new ArrayList<>();
        for (int i = 0; i < teamCount; i++) {
            User captain = fixtures.user("captain" + i);
            captains.add(captain);
            teamList.add(fixtures.team(tournament, "Team " + i, captain));
        }
        PlayerProfile player = fixtures.profile("star-player");
        Lot lot = fixtures.openLot(auction, player, secondsRemaining);
        return new World(tournament, auction, lot, teamList, captains);
    }

    private int credits(Long teamId) {
        return teams.findById(teamId).orElseThrow().getRemainingCredits();
    }

    @Test
    @DisplayName("the highest sealed bid takes the player at its own price")
    void highestSealedBidWins() {
        World w = world(3, 100, 5, 60);

        bidService.submitBid(w.auctionId(), w.lotId(), 30, w.captain(0));
        bidService.submitBid(w.auctionId(), w.lotId(), 55, w.captain(1));
        bidService.submitBid(w.auctionId(), w.lotId(), 12, w.captain(2));

        // Three of three captains locked in, so the reveal has already happened.
        Lot closed = lots.findById(w.lotId()).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(LotStatus.SOLD);
        assertThat(closed.getWinningTeamId()).isEqualTo(w.teamId(1));

        // First price: the winner pays exactly what they wrote, not one over the runner-up.
        assertThat(closed.getWinningBid()).isEqualTo(55);
        assertThat(credits(w.teamId(1))).isEqualTo(45);
        assertThat(credits(w.teamId(0))).isEqualTo(100);
        assertThat(credits(w.teamId(2))).isEqualTo(100);

        List<TeamMember> roster = teamMembers.findByTeamId(w.teamId(1));
        assertThat(roster).hasSize(1);
        assertThat(roster.getFirst().getPricePaid()).isEqualTo(55);
    }

    @Test
    @DisplayName("nobody can see a bid until the lot closes")
    void amountsStaySealedWhileTheLotIsOpen() {
        World w = world(3, 100, 5, 60);
        bidService.submitBid(w.auctionId(), w.lotId(), 42, w.captain(0));

        AuctionSnapshot asRival = auctionService.snapshot(w.auctionId(), w.captain(1));
        LotView lot = asRival.currentLot();

        // Who has committed is public; what they committed is not.
        assertThat(lot.lockedInTeamIds()).containsExactly(w.teamId(0));
        assertThat(lot.captainsExpected()).isEqualTo(3);
        assertThat(lot.winningBid()).isZero();
        assertThat(lot.winningTeamId()).isNull();
        assertThat(asRival.recentBids()).isEmpty();
        assertThat(asRival.yourBid()).isNull();

        // A captain does get their own bid back, so a refresh mid-lot is not a black hole.
        assertThat(auctionService.snapshot(w.auctionId(), w.captain(0)).yourBid()).isEqualTo(42);

        // And the whole board is revealed once it closes.
        auctionService.closeLot(w.lotId());
        assertThat(auctionService.snapshot(w.auctionId(), w.captain(1)).recentBids()).isEmpty();
    }

    @Test
    @DisplayName("a captain may resubmit until the window closes; the last amount counts")
    void lastSubmissionReplacesTheEarlierOne() {
        World w = world(3, 100, 5, 60);

        bidService.submitBid(w.auctionId(), w.lotId(), 90, w.captain(0));
        bidService.submitBid(w.auctionId(), w.lotId(), 10, w.captain(0));  // changed their mind
        bidService.submitBid(w.auctionId(), w.lotId(), 20, w.captain(1));
        bidService.submitBid(w.auctionId(), w.lotId(), 5, w.captain(2));

        Lot closed = lots.findById(w.lotId()).orElseThrow();
        assertThat(closed.getWinningTeamId()).isEqualTo(w.teamId(1));
        assertThat(closed.getWinningBid()).isEqualTo(20);
        assertThat(credits(w.teamId(0))).isEqualTo(100);

        // The withdrawn 90 is still in the audit trail, just not in the reveal.
        assertThat(bids.findByLotIdOrderByIdAsc(w.lotId())).hasSize(4);
    }

    @Test
    @DisplayName("tied top bids are settled by a coin flip, not by who bid first")
    void tiesGoToARandomCaptain() {
        Set<Long> winners = new HashSet<>();

        // A fair coin lands the same way 40 times running with probability 2^-39. If this
        // ever flakes, the tie-break has stopped being random.
        for (int round = 0; round < 40 && winners.size() < 2; round++) {
            World w = world(2, 100, 5, 60);
            bidService.submitBid(w.auctionId(), w.lotId(), 25, w.captain(0));
            bidService.submitBid(w.auctionId(), w.lotId(), 25, w.captain(1));

            Lot closed = lots.findById(w.lotId()).orElseThrow();
            assertThat(closed.getStatus()).isEqualTo(LotStatus.SOLD);
            assertThat(closed.getWinningBid()).isEqualTo(25);
            // Whoever won paid; the other kept everything.
            assertThat(credits(closed.getWinningTeamId())).isEqualTo(75);
            winners.add(closed.getWinningTeamId().equals(w.teamId(0)) ? 0L : 1L);
        }

        assertThat(winners).containsExactlyInAnyOrder(0L, 1L);
    }

    @Test
    @DisplayName("a captain may bid the whole budget - there is no reserve")
    void theWholeBudgetIsBiddable() {
        World w = world(2, 100, 5, 60);

        assertThat(bidService.submitBid(w.auctionId(), w.lotId(), 100, w.captain(0)).amount())
                .isEqualTo(100);
        assertThatThrownBy(() -> bidService.submitBid(w.auctionId(), w.lotId(), 101, w.captain(1)))
                .isInstanceOf(BidRejectedException.class)
                .hasMessageContaining("only have 100 credits");
    }

    @Test
    @DisplayName("the reveal waits for the clock when a captain who could bid has not")
    void aSilentCaptainKeepsTheLotOpen() {
        World w = world(3, 100, 5, 60);

        bidService.submitBid(w.auctionId(), w.lotId(), 10, w.captain(0));
        bidService.submitBid(w.auctionId(), w.lotId(), 20, w.captain(1));

        assertThat(lots.findById(w.lotId()).orElseThrow().getStatus()).isEqualTo(LotStatus.OPEN);

        // Teams with no credits are not waited on, so the count that matters is who *can* bid.
        Team broke = teams.findById(w.teamId(2)).orElseThrow();
        broke.setRemainingCredits(0);
        teams.saveAndFlush(broke);

        bidService.submitBid(w.auctionId(), w.lotId(), 11, w.captain(0));
        assertThat(lots.findById(w.lotId()).orElseThrow().getStatus()).isEqualTo(LotStatus.SOLD);
    }

    @Test
    @DisplayName("once every captain is broke the rest of the pool is dealt out at random")
    void randomFillDealsOutTheRemainderForFree() {
        Tournament tournament = fixtures.tournament(10, 2, 1);
        Auction auction = fixtures.liveAuction(tournament, 30);
        User a = fixtures.user("spender");
        User b = fixtures.user("saver");
        Team teamA = fixtures.team(tournament, "A", a);
        Team teamB = fixtures.team(tournament, "B", b);

        List<Lot> queued = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            queued.add(fixtures.queuedLot(auction, fixtures.profile("waiting" + i)));
        }

        Lot star = fixtures.openLot(auction, fixtures.profile("star"), 60);
        bidService.submitBid(auction.getId(), star.getId(), 10, a.getId());
        bidService.submitBid(auction.getId(), star.getId(), 10, b.getId());
        auctionService.closeLot(star.getId());

        // One captain spent everything, the other still holds credits: no free players yet.
        assertThat(teamMembers.findByTournamentId(tournament.getId())).hasSize(1);
        assertThatThrownBy(() -> auctionService.fillRemainingRandomly(auction.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("can still bid");

        // Drain both captains - which one won the coin flip above is by design unknowable,
        // and the fixture entities here are detached, so their credits are stale. The fill
        // then runs on its own at the next close.
        for (Long id : List.of(teamA.getId(), teamB.getId())) {
            Team broke = teams.findById(id).orElseThrow();
            broke.setRemainingCredits(0);
            teams.saveAndFlush(broke);
        }

        auctionService.nominate(auction.getId(), queued.getFirst().getPlayerProfile().getId());
        auctionService.closeLot(queued.getFirst().getId());

        // Four slots, four players: everyone lands somewhere, and nobody paid for them.
        List<TeamMember> roster = teamMembers.findByTournamentId(tournament.getId());
        assertThat(roster).hasSize(4);
        assertThat(roster.stream().filter(m -> m.getPricePaid() == 0)).hasSize(3);
        assertThat(teamMembers.findByTeamId(teamA.getId())).hasSize(2);
        assertThat(teamMembers.findByTeamId(teamB.getId())).hasSize(2);
        assertThat(lots.findByAuctionIdAndStatus(auction.getId(), LotStatus.PENDING)).isEmpty();
        assertThat(credits(teamA.getId())).isZero();
        assertThat(credits(teamB.getId())).isZero();
    }

    @Test
    @DisplayName("captains take turns nominating, and the admin opens bidding on their pick")
    void captainsTakeTurnsNominating() {
        // Two teams of two: four open slots, four players.
        Tournament tournament = fixtures.tournament(100, 2, 1);
        Auction auction = fixtures.liveAuction(tournament, 30);
        auction.setStatus(AuctionStatus.SETUP);
        auctions.saveAndFlush(auction);
        User capA = fixtures.user("turnCapA");
        User capB = fixtures.user("turnCapB");
        Team a = fixtures.team(tournament, "A", capA);
        Team b = fixtures.team(tournament, "B", capB);
        List<PlayerProfile> pool = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            PlayerProfile player = fixtures.profile("turnPlayer" + i);
            pool.add(player);
            fixtures.queuedLot(auction, player);
        }

        // The first team created nominates first.
        assertThat(auctionService.start(auction.getId()).turnTeamId()).isEqualTo(a.getId());

        // Only that captain may pick, and only players still in the pool.
        assertThatThrownBy(() -> auctionService.pickNomination(auction.getId(), capB.getId(), pool.get(0).getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("A's turn");

        // A captain may change their mind until bidding opens.
        auctionService.pickNomination(auction.getId(), capA.getId(), pool.get(1).getId());
        AuctionDtos.AuctionSnapshot picked =
                auctionService.pickNomination(auction.getId(), capA.getId(), pool.get(3).getId());
        assertThat(picked.pickedPlayer().profileId()).isEqualTo(pool.get(3).getId());

        // The admin's "next" opens that pick, not the head of the queue.
        AuctionDtos.AuctionSnapshot opened = auctionService.nominateNext(auction.getId());
        assertThat(opened.currentLot().player().profileId()).isEqualTo(pool.get(3).getId());
        assertThat(opened.pickedPlayer()).isNull();

        // Once that player is settled, the other team is up; then it comes back round.
        auctionService.closeLot(opened.currentLot().lotId());
        assertThat(auctions.findById(auction.getId()).orElseThrow().getTurnTeamId()).isEqualTo(b.getId());
        AuctionDtos.AuctionSnapshot second = auctionService.nominateNext(auction.getId());
        auctionService.closeLot(second.currentLot().lotId());
        assertThat(auctions.findById(auction.getId()).orElseThrow().getTurnTeamId()).isEqualTo(a.getId());
    }

    @Test
    @DisplayName("the pool has to match the open slots before the auction can start")
    void startRejectsAPoolThatDoesNotFillEverySlot() {
        // Rosters of 3 whose captains already hold a slot: two teams need exactly 4 players.
        Tournament tournament = fixtures.tournament(100, 3, 1);
        Auction auction = fixtures.liveAuction(tournament, 30);
        auction.setStatus(AuctionStatus.SETUP);
        Team a = fixtures.team(tournament, "A", fixtures.user("capA"));
        Team b = fixtures.team(tournament, "B", fixtures.user("capB"));
        teamMembers.saveAndFlush(new TeamMember(a, fixtures.profile("capA-slot"), 0));
        teamMembers.saveAndFlush(new TeamMember(b, fixtures.profile("capB-slot"), 0));

        for (int i = 0; i < 3; i++) {
            fixtures.queuedLot(auction, fixtures.profile("queued" + i));
        }
        assertThatThrownBy(() -> auctionService.start(auction.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("3 player(s) for 4 open slot(s)")
                .hasMessageContaining("Queue 1 more");

        fixtures.queuedLot(auction, fixtures.profile("queued3"));
        assertThat(auctionService.start(auction.getId()).status()).isEqualTo(AuctionStatus.LIVE);
        assertThat(tournaments.findById(tournament.getId()).orElseThrow().getStatus())
                .isEqualTo(TournamentStatus.DRAFTING);

        auction.setStatus(AuctionStatus.SETUP);
        fixtures.queuedLot(auction, fixtures.profile("queued4"));
        assertThatThrownBy(() -> auctionService.start(auction.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("only 4 open slot(s)");
    }

    @Test
    @DisplayName("closing the final player while paused completes the draft")
    void finalSaleWhilePausedCompletesTheDraft() {
        World w = world(2, 100, 1, 60);
        bidService.submitBid(w.auctionId(), w.lotId(), 20, w.captain(0));
        auctionService.pause(w.auctionId());

        auctionService.closeLot(w.lotId());

        assertThat(auctions.findById(w.auctionId()).orElseThrow().getStatus())
                .isEqualTo(AuctionStatus.COMPLETE);
        assertThat(tournaments.findById(w.tournament().getId()).orElseThrow().getStatus())
                .isEqualTo(TournamentStatus.LIVE);
        assertThat(lots.findById(w.lotId()).orElseThrow().getStatus()).isEqualTo(LotStatus.SOLD);
    }

    @Test
    @DisplayName("the draft ends itself once the last player is placed")
    void theLastSaleCompletesTheDraft() {
        // One team, one slot, one player: the sale that fills it is the end of the draft.
        Tournament tournament = fixtures.tournament(100, 1, 1);
        Auction auction = fixtures.liveAuction(tournament, 30);
        User captain = fixtures.user("last-call");
        fixtures.team(tournament, "Last Call", captain);
        Lot lot = fixtures.openLot(auction, fixtures.profile("final-pick"), 60);

        bidService.submitBid(auction.getId(), lot.getId(), 20, captain.getId());

        Auction finished = auctions.findById(auction.getId()).orElseThrow();
        assertThat(finished.getStatus()).isEqualTo(AuctionStatus.COMPLETE);
        assertThat(finished.getCurrentLotId()).isNull();
        // DRAFTING is over; the tournament itself is now on.
        assertThat(tournaments.findById(tournament.getId()).orElseThrow().getStatus())
                .isEqualTo(TournamentStatus.LIVE);
    }

    @Test
    @DisplayName("the draft stays live while a slot and a player are still looking for each other")
    void anUnsoldPlayerKeepsTheDraftOpen() {
        World w = world(2, 100, 5, 60);
        Lot lot = lots.findById(w.lotId()).orElseThrow();
        lot.setEndsAt(Instant.now().minusSeconds(1));
        lots.saveAndFlush(lot);

        sweeper.closeExpiredLots();

        assertThat(auctions.findById(w.auctionId()).orElseThrow().getStatus())
                .isEqualTo(AuctionStatus.LIVE);
    }

    @Test
    @DisplayName("undoing a sale reopens a finished draft")
    void undoAfterCompletionReopensTheDraft() {
        Tournament tournament = fixtures.tournament(100, 1, 1);
        Auction auction = fixtures.liveAuction(tournament, 30);
        User captain = fixtures.user("second-thoughts");
        fixtures.team(tournament, "Second Thoughts", captain);
        Lot lot = fixtures.openLot(auction, fixtures.profile("returned"), 60);
        bidService.submitBid(auction.getId(), lot.getId(), 20, captain.getId());

        assertThat(auctions.findById(auction.getId()).orElseThrow().getStatus())
                .isEqualTo(AuctionStatus.COMPLETE);

        auctionService.undoLastSale(auction.getId());

        // A player is waiting again, so calling it finished would be a lie.
        assertThat(auctions.findById(auction.getId()).orElseThrow().getStatus())
                .isEqualTo(AuctionStatus.LIVE);
        assertThat(tournaments.findById(tournament.getId()).orElseThrow().getStatus())
                .isEqualTo(TournamentStatus.DRAFTING);
        assertThat(lots.findById(lot.getId()).orElseThrow().getStatus()).isEqualTo(LotStatus.PENDING);
    }

    @Test
    @DisplayName("a player nobody bid on can be put up again")
    void unsoldPlayersStayInTheQueue() {
        World w = world(2, 100, 5, 60);
        Lot ignored = lots.findById(w.lotId()).orElseThrow();
        ignored.setEndsAt(Instant.now().minusSeconds(1));
        lots.saveAndFlush(ignored);
        sweeper.closeExpiredLots();

        assertThat(lots.findById(w.lotId()).orElseThrow().getStatus()).isEqualTo(LotStatus.UNSOLD);

        // Still listed for the admin, still counted as "to come", still nominatable - all
        // three are what a captain sees as "I cannot get to the next player".
        assertThat(auctionService.queue(w.auctionId()))
                .extracting(lot -> lot.player().username())
                .contains(w.lot().getPlayerProfile().getUser().getUsername());
        assertThat(auctionService.snapshot(w.auctionId()).pendingLots()).isEqualTo(1);

        auctionService.nominateNext(w.auctionId());
        assertThat(lots.findById(w.lotId()).orElseThrow().getStatus()).isEqualTo(LotStatus.OPEN);

        // And they can still be bought on the second time round.
        bidService.submitBid(w.auctionId(), w.lotId(), 7, w.captain(0));
        bidService.submitBid(w.auctionId(), w.lotId(), 3, w.captain(1));
        assertThat(lots.findById(w.lotId()).orElseThrow().getStatus()).isEqualTo(LotStatus.SOLD);
        assertThat(credits(w.teamId(0))).isEqualTo(93);
    }

    @Test
    @DisplayName("a player put up again after an undo starts a fresh sealed round")
    void reopeningIgnoresThePreviousRoundsBids() {
        World w = world(2, 100, 5, 60);
        bidService.submitBid(w.auctionId(), w.lotId(), 40, w.captain(0));
        bidService.submitBid(w.auctionId(), w.lotId(), 30, w.captain(1));
        auctionService.undoLastSale(w.auctionId());

        auctionService.nominate(w.auctionId(), w.lot().getPlayerProfile().getId());

        // The first round's 40 and 30 must not count as anyone being locked in.
        Lot reopened = lots.findById(w.lotId()).orElseThrow();
        assertThat(reopened.getStatus()).isEqualTo(LotStatus.OPEN);
        AuctionSnapshot snapshot = auctionService.snapshot(w.auctionId(), w.captain(0));
        assertThat(snapshot.currentLot().lockedInTeamIds()).isEmpty();
        assertThat(snapshot.yourBid()).isNull();

        bidService.submitBid(w.auctionId(), w.lotId(), 1, w.captain(0));
        bidService.submitBid(w.auctionId(), w.lotId(), 2, w.captain(1));

        Lot resold = lots.findById(w.lotId()).orElseThrow();
        assertThat(resold.getWinningBid()).isEqualTo(2);
        assertThat(resold.getWinningTeamId()).isEqualTo(w.teamId(1));
        assertThat(credits(w.teamId(0))).isEqualTo(100);
    }

    @Test
    @DisplayName("simultaneous sealed bids settle on exactly one winner")
    void concurrentSubmissionsProduceOneCoherentSale() throws Exception {
        World w = world(4, 100, 5, 60);

        List<int[]> attempts = new ArrayList<>();
        for (int captain = 0; captain < 4; captain++) {
            for (int amount = 1; amount <= 5; amount++) {
                attempts.add(new int[]{captain, amount * 3 + captain});
            }
        }
        Collections.shuffle(attempts);

        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();

        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        for (int[] attempt : attempts) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    bidService.submitBid(w.auctionId(), w.lotId(), attempt[1], w.captain(attempt[0]));
                    accepted.incrementAndGet();
                } catch (RuntimeException expected) {
                    // Once all four are in the lot reveals, and later submissions are refused.
                }
                return null;
            }));
        }
        start.countDown();
        for (var future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(accepted.get()).isPositive();

        Lot closed = lots.findById(w.lotId()).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(LotStatus.SOLD);

        // Exactly one sale, and the winner was charged exactly the revealed price - no
        // double-charge from two threads settling the same lot.
        List<TeamMember> roster = teamMembers.findByTournamentId(w.tournament().getId());
        assertThat(roster).hasSize(1);
        assertThat(roster.getFirst().getPricePaid()).isEqualTo(closed.getWinningBid());
        assertThat(roster.getFirst().getTeam().getId()).isEqualTo(closed.getWinningTeamId());
        assertThat(credits(closed.getWinningTeamId())).isEqualTo(100 - closed.getWinningBid());
    }

    @Test
    @DisplayName("bids are refused once the timer has run out")
    void rejectsBidsAfterTheDeadline() {
        World w = world(2, 100, 5, 60);
        Lot lot = lots.findById(w.lotId()).orElseThrow();
        lot.setEndsAt(Instant.now().minusSeconds(1));
        lots.saveAndFlush(lot);

        assertThatThrownBy(() -> bidService.submitBid(w.auctionId(), lot.getId(), 10, w.captain(0)))
                .isInstanceOf(BidRejectedException.class)
                .hasMessageContaining("Time is up");
    }

    @Test
    @DisplayName("a bid must meet the floor")
    void rejectsBidsBelowTheMinimum() {
        Tournament tournament = fixtures.tournament(100, 5, 2);
        Auction auction = fixtures.liveAuction(tournament, 30);
        User a = fixtures.user("a");
        fixtures.team(tournament, "A", a);
        fixtures.team(tournament, "B", fixtures.user("b"));
        Lot lot = fixtures.openLot(auction, fixtures.profile("player"), 60);

        assertThatThrownBy(() -> bidService.submitBid(auction.getId(), lot.getId(), 1, a.getId()))
                .isInstanceOf(BidRejectedException.class)
                .hasMessageContaining("Minimum bid is 2");

        assertThat(bidService.submitBid(auction.getId(), lot.getId(), 2, a.getId()).amount())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("the sweeper settles an expired lot exactly once")
    void sweeperClosesExpiredLotAndChargesOnce() {
        World w = world(3, 100, 5, 60);
        bidService.submitBid(w.auctionId(), w.lotId(), 25, w.captain(0));

        // Simulate the countdown running out, including across a restart: the sweeper picks
        // the lot up from the database rather than from an in-memory timer.
        Lot lot = lots.findById(w.lotId()).orElseThrow();
        lot.setEndsAt(Instant.now().minusSeconds(1));
        lots.saveAndFlush(lot);

        sweeper.closeExpiredLots();
        sweeper.closeExpiredLots();
        auctionService.closeLot(lot.getId());

        assertThat(lots.findById(lot.getId()).orElseThrow().getStatus()).isEqualTo(LotStatus.SOLD);
        assertThat(credits(w.teamId(0))).isEqualTo(75);
        assertThat(teamMembers.findByTeamId(w.teamId(0))).hasSize(1);
    }

    @Test
    @DisplayName("a lot that attracts no bids closes unsold and charges nobody")
    void unsoldLotChargesNobody() {
        World w = world(2, 100, 5, 60);
        Lot lot = lots.findById(w.lotId()).orElseThrow();
        lot.setEndsAt(Instant.now().minusSeconds(1));
        lots.saveAndFlush(lot);

        sweeper.closeExpiredLots();

        assertThat(lots.findById(lot.getId()).orElseThrow().getStatus()).isEqualTo(LotStatus.UNSOLD);
        assertThat(credits(w.teamId(0))).isEqualTo(100);
        assertThat(teamMembers.findByTournamentId(w.tournament().getId())).isEmpty();
    }

    @Test
    @DisplayName("undo refunds the team and returns the player to the queue")
    void undoLastSaleRefundsAndRequeues() {
        World w = world(2, 100, 5, 60);
        bidService.submitBid(w.auctionId(), w.lotId(), 30, w.captain(0));
        bidService.submitBid(w.auctionId(), w.lotId(), 10, w.captain(1));

        assertThat(credits(w.teamId(0))).isEqualTo(70);

        auctionService.undoLastSale(w.auctionId());

        assertThat(credits(w.teamId(0))).isEqualTo(100);
        assertThat(teamMembers.findByTournamentId(w.tournament().getId())).isEmpty();

        Lot requeued = lots.findById(w.lotId()).orElseThrow();
        assertThat(requeued.getStatus()).isEqualTo(LotStatus.PENDING);
        assertThat(requeued.getWinningBid()).isZero();
        assertThat(requeued.getWinningTeamId()).isNull();
        assertThat(requeued.getOpenedAt()).isNull();

        // The audit log is deliberately left intact.
        assertThat(bids.findByLotIdOrderByIdAsc(w.lotId())).isNotEmpty();
    }

    @Test
    @DisplayName("pausing freezes the countdown and takes the lot out of the sweeper's reach")
    void pauseFreezesTheCountdown() {
        World w = world(2, 100, 5, 5);

        auctionService.pause(w.auctionId());

        Lot paused = lots.findById(w.lotId()).orElseThrow();
        assertThat(paused.getEndsAt()).isNull();
        assertThat(paused.getPausedRemainingMs()).isNotNull().isGreaterThan(0L);

        sweeper.closeExpiredLots();
        assertThat(lots.findById(w.lotId()).orElseThrow().getStatus()).isEqualTo(LotStatus.OPEN);

        auctionService.resume(w.auctionId());

        Lot resumed = lots.findById(w.lotId()).orElseThrow();
        assertThat(resumed.getEndsAt()).isNotNull();
        assertThat(resumed.getPausedRemainingMs()).isNull();
    }

    @Test
    @DisplayName("a full roster stops a team bidding even with credits left over")
    void fullRosterCannotBid() {
        Tournament tournament = fixtures.tournament(100, 1, 1);
        Auction auction = fixtures.liveAuction(tournament, 30);
        User captain = fixtures.user("done");
        fixtures.team(tournament, "Done", captain);

        Lot first = fixtures.openLot(auction, fixtures.profile("p1"), 60);
        bidService.submitBid(auction.getId(), first.getId(), 10, captain.getId());
        auctionService.closeLot(first.getId());

        Lot second = fixtures.openLot(auction, fixtures.profile("p2"), 60);
        assertThatThrownBy(() -> bidService.submitBid(auction.getId(), second.getId(), 10, captain.getId()))
                .isInstanceOf(BidRejectedException.class)
                .hasMessageContaining("roster is already full");
    }
}
