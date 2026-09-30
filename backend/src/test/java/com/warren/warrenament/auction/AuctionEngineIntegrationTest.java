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
import com.warren.warrenament.team.TeamDtos;
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
import java.util.List;
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
    @Autowired com.warren.warrenament.team.TeamService teamService;

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

    private void expire(Long lotId) {
        Lot lot = lots.findById(lotId).orElseThrow();
        lot.setEndsAt(Instant.now().minusSeconds(1));
        lots.saveAndFlush(lot);
    }

    private int credits(Long teamId) {
        return teams.findById(teamId).orElseThrow().getRemainingCredits();
    }

    @Test
    @DisplayName("the highest bid when the clock runs out takes the player at that price")
    void highestBidWins() {
        World w = world(3, 100, 5, 60);

        bidService.submitBid(w.auctionId(), w.lotId(), 30, w.captain(0));
        bidService.submitBid(w.auctionId(), w.lotId(), 55, w.captain(1));
        assertThat(lots.findById(w.lotId()).orElseThrow().getStatus()).isEqualTo(LotStatus.OPEN);

        expire(w.lotId());
        sweeper.closeExpiredLots();

        Lot closed = lots.findById(w.lotId()).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(LotStatus.SOLD);
        assertThat(closed.getWinningTeamId()).isEqualTo(w.teamId(1));
        assertThat(closed.getWinningBid()).isEqualTo(55);
        assertThat(credits(w.teamId(1))).isEqualTo(45);
        // Being outbid costs nothing.
        assertThat(credits(w.teamId(0))).isEqualTo(100);
        assertThat(teamMembers.findByTeamId(w.teamId(1))).singleElement()
                .extracting(TeamMember::getPricePaid).isEqualTo(55);
    }

    @Test
    @DisplayName("the price and the leader are public while bidding is open")
    void theRoomSeesTheCurrentPrice() {
        World w = world(3, 100, 5, 60);
        bidService.submitBid(w.auctionId(), w.lotId(), 42, w.captain(0));

        AuctionSnapshot snapshot = auctionService.snapshot(w.auctionId());
        LotView lot = snapshot.currentLot();
        assertThat(lot.winningBid()).isEqualTo(42);
        assertThat(lot.winningTeamId()).isEqualTo(w.teamId(0));
        assertThat(lot.minBid()).isEqualTo(43);
        assertThat(snapshot.recentBids()).singleElement()
                .satisfies(bid -> assertThat(bid.amount()).isEqualTo(42));
    }

    @Test
    @DisplayName("every bid has to beat the current price by at least one credit")
    void bidsMustClimb() {
        World w = world(2, 100, 5, 60);
        bidService.submitBid(w.auctionId(), w.lotId(), 10, w.captain(0));

        assertThatThrownBy(() -> bidService.submitBid(w.auctionId(), w.lotId(), 10, w.captain(1)))
                .isInstanceOf(BidRejectedException.class)
                .hasMessageContaining("Bid at least 11");
        assertThat(bidService.submitBid(w.auctionId(), w.lotId(), 11, w.captain(1)).amount()).isEqualTo(11);
    }

    @Test
    @DisplayName("the team holding the player can't bid against itself")
    void theLeaderCannotRaiseItself() {
        World w = world(2, 100, 5, 60);
        bidService.submitBid(w.auctionId(), w.lotId(), 10, w.captain(0));

        assertThatThrownBy(() -> bidService.submitBid(w.auctionId(), w.lotId(), 20, w.captain(0)))
                .isInstanceOf(BidRejectedException.class)
                .hasMessageContaining("already hold");

        // Once outbid, they're free to come back in.
        bidService.submitBid(w.auctionId(), w.lotId(), 15, w.captain(1));
        assertThat(bidService.submitBid(w.auctionId(), w.lotId(), 20, w.captain(0)).amount()).isEqualTo(20);
    }

    @Test
    @DisplayName("with no bids, the nominating team keeps the player for nothing")
    void theNominatorKeepsAnUncontestedPlayer() {
        Tournament tournament = fixtures.tournament(100, 5, 1);
        Auction auction = fixtures.liveAuction(tournament, 30);
        Team nominator = fixtures.team(tournament, "Nominator", fixtures.user("nominator"));
        fixtures.team(tournament, "Rival", fixtures.user("rival"));
        PlayerProfile player = fixtures.profile("quiet-pick");
        fixtures.queuedLot(auction, player);
        auction.setTurnTeamId(nominator.getId());
        auctions.saveAndFlush(auction);

        LotView opened = auctionService.nominate(auction.getId(), player.getId()).currentLot();
        // Held by the nominator at 0 from the moment it opens.
        assertThat(opened.winningTeamId()).isEqualTo(nominator.getId());
        assertThat(opened.winningBid()).isZero();
        assertThat(opened.minBid()).isEqualTo(1);

        expire(opened.lotId());
        sweeper.closeExpiredLots();

        assertThat(lots.findById(opened.lotId()).orElseThrow().getStatus()).isEqualTo(LotStatus.SOLD);
        assertThat(teamMembers.findByTeamId(nominator.getId())).singleElement()
                .extracting(TeamMember::getPricePaid).isEqualTo(0);
        assertThat(credits(nominator.getId())).isEqualTo(100);
    }

    @Test
    @DisplayName("a bid restarts a short countdown but never shortens the clock")
    void aBidBuysTheRoomTimeToAnswer() {
        World closing = world(2, 100, 5, 3);
        bidService.submitBid(closing.auctionId(), closing.lotId(), 5, closing.captain(0));
        assertThat(lots.findById(closing.lotId()).orElseThrow().getEndsAt())
                .isAfter(Instant.now().plusSeconds(BidService.BID_RESET_SECONDS - 2));

        World early = world(2, 100, 5, 60);
        bidService.submitBid(early.auctionId(), early.lotId(), 5, early.captain(0));
        assertThat(lots.findById(early.lotId()).orElseThrow().getEndsAt())
                .isAfter(Instant.now().plusSeconds(50));
    }

    @Test
    @DisplayName("the lot closes at once when nobody else could outbid")
    void anUnbeatableBidSettlesImmediately() {
        World w = world(2, 100, 5, 60);
        Team rival = teams.findById(w.teamId(1)).orElseThrow();
        rival.setRemainingCredits(10);
        teams.saveAndFlush(rival);

        bidService.submitBid(w.auctionId(), w.lotId(), 10, w.captain(0));

        // The rival would need 11 and holds 10: no point running the clock out.
        Lot closed = lots.findById(w.lotId()).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(LotStatus.SOLD);
        assertThat(closed.getWinningTeamId()).isEqualTo(w.teamId(0));
    }

    @Test
    @DisplayName("once nobody else can bid, a nominated player goes straight to the nominator")
    void aNominationNobodyCanContestSettlesAtZero() {
        Tournament tournament = fixtures.tournament(100, 5, 1);
        Auction auction = fixtures.liveAuction(tournament, 30);
        Team nominator = fixtures.team(tournament, "Nominator", fixtures.user("last-nominator"));
        Team broke = fixtures.team(tournament, "Broke", fixtures.user("broke"));
        broke.setRemainingCredits(0);
        teams.saveAndFlush(broke);
        PlayerProfile player = fixtures.profile("free-pick");
        fixtures.queuedLot(auction, player);
        auction.setTurnTeamId(nominator.getId());
        auctions.saveAndFlush(auction);

        auctionService.nominate(auction.getId(), player.getId());

        assertThat(teamMembers.findByTeamId(nominator.getId())).singleElement()
                .extracting(TeamMember::getPricePaid).isEqualTo(0);
    }

    @Test
    @DisplayName("a captain may bid the whole budget - there is no reserve")
    void theWholeBudgetIsBiddable() {
        World w = world(2, 100, 5, 60);

        assertThatThrownBy(() -> bidService.submitBid(w.auctionId(), w.lotId(), 101, w.captain(1)))
                .isInstanceOf(BidRejectedException.class)
                .hasMessageContaining("only have 100 credits");
        assertThat(bidService.submitBid(w.auctionId(), w.lotId(), 100, w.captain(0)).amount())
                .isEqualTo(100);
    }

    @Test
    @DisplayName("racing bids are applied one at a time, each beating the last")
    void concurrentBidsClimbInOrder() throws Exception {
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
                    // Too low by the time it was applied, or that team was already leading.
                }
                return null;
            }));
        }
        start.countDown();
        for (var future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        List<Bid> placed = bids.findByLotIdOrderByIdAsc(w.lotId());
        assertThat(placed).hasSize(accepted.get()).isNotEmpty();
        // The lock is what makes this hold: each accepted bid saw the price the last one set.
        for (int i = 1; i < placed.size(); i++) {
            assertThat(placed.get(i).getAmount()).isGreaterThan(placed.get(i - 1).getAmount());
            assertThat(placed.get(i).getTeamId()).isNotEqualTo(placed.get(i - 1).getTeamId());
        }
        Lot lot = lots.findById(w.lotId()).orElseThrow();
        assertThat(lot.getWinningBid()).isEqualTo(placed.getLast().getAmount());
        assertThat(lot.getWinningTeamId()).isEqualTo(placed.getLast().getTeamId());

        auctionService.closeLot(w.lotId());
        auctionService.closeLot(w.lotId());
        List<TeamMember> roster = teamMembers.findByTournamentId(w.tournament().getId());
        assertThat(roster).hasSize(1);
        assertThat(credits(lot.getWinningTeamId())).isEqualTo(100 - lot.getWinningBid());
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
    @DisplayName("nominating turns follow the admin's team order, then come back round")
    void turnsFollowTheDraftOrder() {
        Tournament tournament = fixtures.tournament(100, 3, 1);
        Auction auction = fixtures.liveAuction(tournament, 30);
        auction.setStatus(AuctionStatus.SETUP);
        auctions.saveAndFlush(auction);
        Team a = fixtures.team(tournament, "A", fixtures.user("orderA"));
        Team b = fixtures.team(tournament, "B", fixtures.user("orderB"));
        Team c = fixtures.team(tournament, "C", fixtures.user("orderC"));
        for (int i = 0; i < 6; i++) {
            fixtures.queuedLot(auction, fixtures.profile("ordered" + i));
        }

        teamService.reorder(tournament.getId(), List.of(c.getId(), a.getId(), b.getId()));
        assertThat(teamService.findByTournament(tournament.getId()))
                .extracting(TeamDtos.TeamView::id).containsExactly(c.getId(), a.getId(), b.getId());

        assertThat(auctionService.start(auction.getId()).turnTeamId()).isEqualTo(c.getId());
        List<Long> turns = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            AuctionSnapshot opened = auctionService.nominateNext(auction.getId());
            auctionService.closeLot(opened.currentLot().lotId());
            turns.add(auctions.findById(auction.getId()).orElseThrow().getTurnTeamId());
        }
        assertThat(turns).containsExactly(a.getId(), b.getId(), c.getId());

        // Once the draft is running the order is fixed.
        assertThatThrownBy(() -> teamService.reorder(tournament.getId(), List.of(a.getId(), b.getId(), c.getId())))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("team size comes from the pool at the start, rounded up so nobody is turned away")
    void startSizesTeamsFromThePool() {
        // Whatever size the tournament was created with is replaced at the start.
        Tournament tournament = fixtures.tournament(100, 9, 1);
        Auction auction = fixtures.liveAuction(tournament, 30);
        auction.setStatus(AuctionStatus.SETUP);
        Team a = fixtures.team(tournament, "A", fixtures.user("capA"));
        Team b = fixtures.team(tournament, "B", fixtures.user("capB"));
        teamMembers.saveAndFlush(new TeamMember(a, fixtures.profile("capA-slot"), 0));
        teamMembers.saveAndFlush(new TeamMember(b, fixtures.profile("capB-slot"), 0));

        assertThatThrownBy(() -> auctionService.start(auction.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Nobody is in the queue");

        // Two captains and three players don't divide evenly: teams of three, one a player short.
        for (int i = 0; i < 3; i++) {
            fixtures.queuedLot(auction, fixtures.profile("queued" + i));
        }
        assertThat(auctionService.start(auction.getId()).status()).isEqualTo(AuctionStatus.LIVE);
        Tournament started = tournaments.findById(tournament.getId()).orElseThrow();
        assertThat(started.getRosterSize()).isEqualTo(3);
        assertThat(started.getStatus()).isEqualTo(TournamentStatus.DRAFTING);
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
        bidService.submitBid(w.auctionId(), w.lotId(), 9, w.captain(1));
        auctionService.closeLot(w.lotId());
        assertThat(lots.findById(w.lotId()).orElseThrow().getStatus()).isEqualTo(LotStatus.SOLD);
        assertThat(credits(w.teamId(1))).isEqualTo(91);
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
                .hasMessageContaining("Bid at least 2");

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
        // Another team with room and a player still waiting keep the draft going.
        fixtures.team(tournament, "Other", fixtures.user("other"));
        fixtures.queuedLot(auction, fixtures.profile("p3"));

        Lot first = fixtures.openLot(auction, fixtures.profile("p1"), 60);
        bidService.submitBid(auction.getId(), first.getId(), 10, captain.getId());
        auctionService.closeLot(first.getId());

        Lot second = fixtures.openLot(auction, fixtures.profile("p2"), 60);
        assertThatThrownBy(() -> bidService.submitBid(auction.getId(), second.getId(), 10, captain.getId()))
                .isInstanceOf(BidRejectedException.class)
                .hasMessageContaining("roster is already full");
    }
}
