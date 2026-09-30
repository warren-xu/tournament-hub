package com.warren.warrenament;

import com.warren.warrenament.auction.Auction;
import com.warren.warrenament.auction.AuctionRepository;
import com.warren.warrenament.auction.Lot;
import com.warren.warrenament.auction.LotRepository;
import com.warren.warrenament.auction.LotStatus;
import com.warren.warrenament.auth.Role;
import com.warren.warrenament.auth.User;
import com.warren.warrenament.auth.UserRepository;
import com.warren.warrenament.profile.PlayerProfile;
import com.warren.warrenament.profile.PlayerProfileRepository;
import com.warren.warrenament.team.Team;
import com.warren.warrenament.team.TeamRepository;
import com.warren.warrenament.tournament.Tournament;
import com.warren.warrenament.tournament.TournamentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/** Builds the minimum world an auction test needs. */
@Component
public class TestFixtures {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private final UserRepository users;
    private final PlayerProfileRepository profiles;
    private final TournamentRepository tournaments;
    private final TeamRepository teams;
    private final AuctionRepository auctions;
    private final LotRepository lots;

    public TestFixtures(UserRepository users,
                        PlayerProfileRepository profiles,
                        TournamentRepository tournaments,
                        TeamRepository teams,
                        AuctionRepository auctions,
                        LotRepository lots) {
        this.users = users;
        this.profiles = profiles;
        this.tournaments = tournaments;
        this.teams = teams;
        this.auctions = auctions;
        this.lots = lots;
    }

    @Transactional
    public User user(String name) {
        return users.save(new User("discord-" + name + "-" + COUNTER.incrementAndGet(),
                name, null, Role.PLAYER));
    }

    @Transactional
    public PlayerProfile profile(String name) {
        return profiles.save(new PlayerProfile(user(name)));
    }

    @Transactional
    public Tournament tournament(int creditBudget, int rosterSize, int minBid) {
        Tournament t = new Tournament();
        t.setName("Test Cup");
        t.setSlug("test-cup-" + COUNTER.incrementAndGet());
        t.setCreditBudget(creditBudget);
        t.setRosterSize(rosterSize);
        t.setMinBid(minBid);
        return tournaments.save(t);
    }

    @Transactional
    public Team team(Tournament tournament, String name, User captain) {
        return teams.save(new Team(tournament, name, null, captain.getId()));
    }

    /** A player queued but not yet nominated. */
    @Transactional
    public Lot queuedLot(Auction auction, PlayerProfile player) {
        return lots.saveAndFlush(new Lot(auction, player, lots.maxSeq(auction.getId()) + 1));
    }

    @Transactional
    public Auction liveAuction(Tournament tournament, int lotDurationSeconds) {
        Auction auction = new Auction(tournament);
        auction.setStatus(com.warren.warrenament.auction.AuctionStatus.LIVE);
        auction.setLotDurationSeconds(lotDurationSeconds);
        return auctions.save(auction);
    }

    /** An already-open lot nobody holds yet (no nominating team), for the given duration. */
    @Transactional
    public Lot openLot(Auction auction, PlayerProfile player, int secondsRemaining) {
        Lot lot = new Lot(auction, player, lots.maxSeq(auction.getId()) + 1);
        lot.setStatus(LotStatus.OPEN);
        // Bids are scoped to the round, so an open lot must carry its start time.
        lot.setOpenedAt(Instant.now());
        lot.setEndsAt(Instant.now().plusSeconds(secondsRemaining));
        lot = lots.saveAndFlush(lot);

        auction.setCurrentLotId(lot.getId());
        auctions.saveAndFlush(auction);
        return lot;
    }
}
