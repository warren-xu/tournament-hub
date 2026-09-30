package com.warren.warrenament.auction;

import com.warren.warrenament.auction.AuctionDtos.AuctionMessage;
import com.warren.warrenament.auction.AuctionDtos.AuctionSnapshot;
import com.warren.warrenament.common.Exceptions.BadRequestException;
import com.warren.warrenament.common.Exceptions.NotFoundException;
import com.warren.warrenament.profile.PlayerProfile;
import com.warren.warrenament.rank.Rank;
import com.warren.warrenament.rank.RankRepository;
import com.warren.warrenament.team.Team;
import com.warren.warrenament.team.TeamMember;
import com.warren.warrenament.team.TeamMemberRepository;
import com.warren.warrenament.team.TeamRepository;
import com.warren.warrenament.tournament.Registration;
import com.warren.warrenament.tournament.RegistrationRepository;
import com.warren.warrenament.tournament.RegistrationStatus;
import com.warren.warrenament.tournament.Tournament;
import com.warren.warrenament.tournament.TournamentStatus;
import com.warren.warrenament.tournament.TournamentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.warren.warrenament.auction.AuctionDtos.BidView;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.HashMap;
import java.util.Comparator;

/** Admin-driven lifecycle of an auction: build the queue, nominate, pause, close. */
@Service
public class AuctionService {

    private static final Logger log = LoggerFactory.getLogger(AuctionService.class);

    private final AuctionRepository auctions;
    private final LotRepository lots;
    private final TournamentRepository tournaments;
    private final RegistrationRepository registrations;
    private final TeamRepository teams;
    private final TeamMemberRepository teamMembers;
    private final AuctionViewMapper mapper;
    private final ApplicationEventPublisher events;
    private final RankRepository ranks;

    public AuctionService(AuctionRepository auctions,
                          LotRepository lots,
                          TournamentRepository tournaments,
                          RegistrationRepository registrations,
                          TeamRepository teams,
                          TeamMemberRepository teamMembers,
                          AuctionViewMapper mapper,
                          ApplicationEventPublisher events,
                          RankRepository ranks) {
        this.auctions = auctions;
        this.lots = lots;
        this.tournaments = tournaments;
        this.registrations = registrations;
        this.teams = teams;
        this.teamMembers = teamMembers;
        this.mapper = mapper;
        this.events = events;
        this.ranks = ranks;
    }

    @Transactional(readOnly = true)
    public AuctionSnapshot snapshot(Long auctionId) {
        return mapper.toSnapshot(require(auctionId));
    }


    /**
     * Players still to place, in nomination order, so an admin can pick who goes up next.
     * <p>
     * Includes the ones who went unsold: leaving them out of this list is what strands a
     * draft with an empty slot and nobody to put in it.
     */
    @Transactional(readOnly = true)
    public List<AuctionDtos.LotView> queue(Long auctionId) {
        Auction auction = require(auctionId);
        return remainingLots(auctionId).stream()
                .map(lot -> mapper.toLotView(lot, auction.getTournament()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AuctionSnapshot snapshotByTournament(Long tournamentId) {
        Auction auction = auctions.findByTournamentId(tournamentId)
                .orElseThrow(() -> new NotFoundException("No auction for tournament " + tournamentId));
        return mapper.toSnapshot(auction);
    }

    @Transactional
    public AuctionSnapshot createForTournament(Long tournamentId) {
        Tournament tournament = tournaments.findById(tournamentId)
                .orElseThrow(() -> NotFoundException.of("Tournament", tournamentId));
        auctions.findByTournamentId(tournamentId).ifPresent(a -> {
            throw new BadRequestException("This tournament already has an auction");
        });
        Auction auction = auctions.save(new Auction(tournament));
        // Everyone already in the pool goes straight into the queue, in sign-up order.
        return buildQueue(auction.getId());
    }

    /**
     * Adds a player who just joined the pool to the back of the queue. Only while the
     * draft is being set up: once it starts the queue is fixed, and with no auction yet
     * {@link #createForTournament} picks them up from their registration.
     */
    @Transactional
    public void enqueue(Long tournamentId, PlayerProfile player) {
        auctions.findByTournamentId(tournamentId)
                .filter(auction -> auction.getStatus() == AuctionStatus.SETUP)
                .filter(auction -> lots.findByAuctionIdAndPlayerProfileId(auction.getId(), player.getId()).isEmpty())
                .ifPresent(auction -> lots.save(new Lot(auction, player, lots.maxSeq(auction.getId()) + 1)));
    }

    /** Takes a player back out of the queue. Refuses once the draft has started. */
    @Transactional
    public void dequeue(Long tournamentId, Long playerProfileId) {
        requireNotStarted(tournamentId);
        auctions.findByTournamentId(tournamentId)
                .flatMap(auction -> lots.findByAuctionIdAndPlayerProfileId(auction.getId(), playerProfileId))
                .ifPresent(lots::delete);
    }

    /** For changes to the pool or the teams, which only make sense before the first nomination. */
    @Transactional(readOnly = true)
    public void requireNotStarted(Long tournamentId) {
        auctions.findByTournamentId(tournamentId)
                .filter(auction -> auction.getStatus() != AuctionStatus.SETUP)
                .ifPresent(auction -> {
                    throw new BadRequestException("The draft has already started, so the pool and teams are locked.");
                });
    }

    /**
     * Builds the lot queue from approved registrations that are not already on a roster.
     * Safe to re-run while in SETUP; it replaces any lots that have not been nominated yet.
     * The order they're stored in doesn't matter: the queue is always read highest rank first.
     */
    @Transactional
    public AuctionSnapshot buildQueue(Long auctionId) {
        Auction auction = require(auctionId);
        if (auction.getStatus() != AuctionStatus.SETUP) {
            throw new BadRequestException("The queue can only be rebuilt while the auction is in SETUP");
        }
        Tournament tournament = auction.getTournament();

        lots.deleteAll(lots.findByAuctionIdAndStatus(auctionId, LotStatus.PENDING));
        lots.flush();

        List<PlayerProfile> pool = new ArrayList<>(
                registrations.findByTournamentIdAndStatus(tournament.getId(), RegistrationStatus.APPROVED)
                        .stream()
                        .map(Registration::getPlayerProfile)
                        .filter(p -> !teamMembers.existsByTournamentIdAndPlayerProfileId(
                                tournament.getId(), p.getId()))
                        .filter(p -> lots.findByAuctionIdAndPlayerProfileId(auctionId, p.getId()).isEmpty())
                        .toList());

        int seq = lots.maxSeq(auctionId);
        for (PlayerProfile player : pool) {
            lots.save(new Lot(auction, player, ++seq));
        }
        return mapper.toSnapshot(auction);
    }

    @Transactional
    public AuctionSnapshot start(Long auctionId) {
        Auction auction = require(auctionId);
        if (auction.getStatus() == AuctionStatus.COMPLETE) {
            throw new BadRequestException("This auction is already complete");
        }
        Tournament tournament = auction.getTournament();
        List<Team> roster = teams.findByTournamentId(tournament.getId());
        if (roster.isEmpty()) {
            throw new BadRequestException("Create at least one team before starting the auction");
        }
        if (auction.getStatus() == AuctionStatus.SETUP) {
            fitRosterSizeToPool(auction, tournament, roster);
        }
        requireRoomForEveryone(auction, tournament, roster);
        auction.setStatus(AuctionStatus.LIVE);
        if (auction.getTurnTeamId() == null) {
            advanceTurn(auction);
        }
        auctions.saveAndFlush(auction);
        tournament.setStatus(TournamentStatus.DRAFTING);
        tournaments.saveAndFlush(tournament);
        publishStatus(auction, "Draft started");
        return mapper.toSnapshot(auction);
    }

    /** Freezes the countdown on the open lot so nobody loses seconds to an admin timeout. */
    @Transactional
    public AuctionSnapshot pause(Long auctionId) {
        Auction auction = require(auctionId);
        if (auction.getStatus() != AuctionStatus.LIVE) {
            throw new BadRequestException("Only a live auction can be paused");
        }
        auction.setStatus(AuctionStatus.PAUSED);

        currentLot(auction).ifPresent(lot -> {
            if (lot.getStatus() == LotStatus.OPEN && lot.getEndsAt() != null) {
                long remaining = Math.max(0, Duration.between(Instant.now(), lot.getEndsAt()).toMillis());
                lot.setPausedRemainingMs(remaining);
                // Clearing endsAt is what takes the lot out of the sweeper's view.
                lot.setEndsAt(null);
                lots.saveAndFlush(lot);
            }
        });

        auctions.saveAndFlush(auction);
        publishStatus(auction, "Paused");
        return mapper.toSnapshot(auction);
    }

    @Transactional
    public AuctionSnapshot resume(Long auctionId) {
        Auction auction = require(auctionId);
        if (auction.getStatus() != AuctionStatus.PAUSED) {
            throw new BadRequestException("Only a paused auction can be resumed");
        }
        auction.setStatus(AuctionStatus.LIVE);

        currentLot(auction).ifPresent(lot -> {
            if (lot.getStatus() == LotStatus.OPEN) {
                long remaining = lot.getPausedRemainingMs() == null
                        ? auction.getLotDurationSeconds() * 1000L
                        : lot.getPausedRemainingMs();
                lot.setEndsAt(Instant.now().plusMillis(remaining));
                lot.setPausedRemainingMs(null);
                lots.saveAndFlush(lot);
            }
        });

        auctions.saveAndFlush(auction);
        publishStatus(auction, "Resumed");
        return mapper.toSnapshot(auction);
    }

    /** Puts a specific player up for bidding. */
    @Transactional
    public AuctionSnapshot nominate(Long auctionId, Long playerProfileId) {
        Auction auction = require(auctionId);
        requireLive(auction);
        requireNoOpenLot(auction);

        Lot lot = lots.findByAuctionIdAndPlayerProfileId(auctionId, playerProfileId)
                .orElseThrow(() -> new NotFoundException(
                        "Player " + playerProfileId + " is not in this auction's queue"));
        return open(auction, lot);
    }

    /**
     * Opens bidding on the nominating captain's pick. With no pick made, falls back to the
     * next player in queue order, so an absent captain can't stall the draft.
     */
    @Transactional
    public AuctionSnapshot nominateNext(Long auctionId) {
        Auction auction = require(auctionId);
        requireLive(auction);
        requireNoOpenLot(auction);

        if (auction.getPickLotId() != null) {
            Lot picked = lots.findById(auction.getPickLotId()).orElse(null);
            if (picked != null) {
                return open(auction, picked);
            }
        }
        // Unsold players come after everyone still unseen: they go back up rather than vanish.
        Lot lot = remainingLots(auctionId).stream().findFirst()
                .orElseThrow(() -> new BadRequestException("No players left in the queue"));
        return open(auction, lot);
    }

    /**
     * The nominating captain picks who goes up next. They may change their mind until the
     * admin opens bidding; nothing is bid on until then.
     */
    @Transactional
    public AuctionSnapshot pickNomination(Long auctionId, Long captainUserId, Long playerProfileId) {
        Auction auction = require(auctionId);
        requireLive(auction);
        requireNoOpenLot(auction);

        Team turn = auction.getTurnTeamId() == null ? null
                : teams.findById(auction.getTurnTeamId()).orElse(null);
        if (turn == null || !turn.getCaptainUserId().equals(captainUserId)) {
            throw new BadRequestException(turn == null
                    ? "Nobody is nominating right now"
                    : "It's " + turn.getName() + "'s turn to nominate");
        }
        Lot lot = lots.findByAuctionIdAndPlayerProfileId(auctionId, playerProfileId)
                .filter(l -> l.getStatus() == LotStatus.PENDING || l.getStatus() == LotStatus.UNSOLD)
                .orElseThrow(() -> new BadRequestException("That player isn't available to nominate"));

        auction.setPickLotId(lot.getId());
        auctions.saveAndFlush(auction);
        publishStatus(auction, "%s nominated %s".formatted(
                turn.getName(), lot.getPlayerProfile().getUser().getUsername()));
        return mapper.toSnapshot(auction);
    }

    /**
     * Hands the nomination to the next team in draft order (1, 2 ... n, then round again),
     * skipping teams whose roster is full. Clears any unused pick.
     */
    private void advanceTurn(Auction auction) {
        Tournament tournament = auction.getTournament();
        Map<Long, Integer> rosterCounts = mapper.rosterCounts(tournament.getId());
        List<Team> order = teams.findByTournamentId(tournament.getId());

        auction.setPickLotId(null);
        int start = -1;
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i).getId().equals(auction.getTurnTeamId())) {
                start = i;
            }
        }
        for (int step = 1; step <= order.size(); step++) {
            Team next = order.get(Math.floorMod(start + step, order.size()));
            if (rosterCounts.getOrDefault(next.getId(), 0) < tournament.getRosterSize()) {
                auction.setTurnTeamId(next.getId());
                return;
            }
        }
        auction.setTurnTeamId(null);
    }

    private AuctionSnapshot open(Auction auction, Lot lot) {
        if (lot.getStatus() == LotStatus.SOLD) {
            throw new BadRequestException("That player has already been sold");
        }
        if (teamMembers.existsByTournamentIdAndPlayerProfileId(
                auction.getTournament().getId(), lot.getPlayerProfile().getId())) {
            throw new BadRequestException("That player is already on a roster");
        }

        Instant now = Instant.now();
        lot.setStatus(LotStatus.OPEN);
        // The nominating team starts holding the player at 0: if nobody bids, they keep them.
        lot.setWinningBid(0);
        lot.setWinningTeamId(auction.getTurnTeamId());
        lot.setPausedRemainingMs(null);
        // Stamped before endsAt so no bid can ever land outside the round it belongs to.
        lot.setOpenedAt(now);
        lot.setEndsAt(now.plusSeconds(auction.getLotDurationSeconds()));
        lots.saveAndFlush(lot);

        auction.setCurrentLotId(lot.getId());
        // Whoever nominated, the pick is spent; the turn moves on once this lot closes.
        auction.setPickLotId(null);
        auctions.saveAndFlush(auction);

        events.publishEvent(new AuctionEvents(auction.getId(), new AuctionMessage(
                AuctionMessage.Type.LOT_OPENED,
                auction.getId(),
                auction.getStatus(),
                mapper.toLotView(lot, auction.getTournament()),
                null,
                mapper.toTeamViews(auction.getTournament()),
                lot.getPlayerProfile().getUser().getUsername() + " is up",
                Instant.now())));

        closeIfUncontested(lot);
        return mapper.toSnapshot(require(auction.getId()));
    }

    /**
     * Closes an open lot early when no team other than the one holding it could still
     * outbid: none has both a free slot and the credits for the next bid. Late in a draft,
     * when everyone else is broke or full, this is what keeps the room from watching one
     * dead clock after another.
     */
    @Transactional
    public void closeIfUncontested(Lot lot) {
        if (lot.getStatus() != LotStatus.OPEN) {
            return;
        }
        Tournament tournament = lot.getAuction().getTournament();
        int next = mapper.nextMinimum(lot, tournament);
        Map<Long, Integer> rosterCounts = mapper.rosterCounts(tournament.getId());
        boolean contested = teams.findByTournamentId(tournament.getId()).stream()
                .filter(team -> !team.getId().equals(lot.getWinningTeamId()))
                .anyMatch(team -> BudgetRules.canBid(
                        team.getRemainingCredits(),
                        rosterCounts.getOrDefault(team.getId(), 0),
                        tournament.getRosterSize(),
                        next));
        if (!contested) {
            closeLot(lot.getId());
        }
    }

    /**
     * Settles a lot: whoever holds it when it closes takes the player at the current price.
     * That's the top bidder, or the nominating team at 0 if nobody bid. A lot nobody holds
     * (no team was nominating) goes unsold and back to the queue.
     * <p>
     * Idempotent: a lot that is no longer OPEN is left alone, so the sweeper, an early
     * close and an admin can all call this without double-charging.
     */
    @Transactional
    public void closeLot(Long lotId) {
        Lot lot = lots.findByIdForUpdate(lotId)
                .orElseThrow(() -> NotFoundException.of("Lot", lotId));

        if (lot.getStatus() != LotStatus.OPEN) {
            return;
        }

        Auction auction = lot.getAuction();
        Tournament tournament = auction.getTournament();
        String player = lot.getPlayerProfile().getUser().getUsername();
        String note;

        Team team = lot.getWinningTeamId() == null ? null
                : teams.findByIdForUpdate(lot.getWinningTeamId()).orElse(null);
        // Bidding checks both of these, so failing here means state moved under the lot.
        boolean placeable = team != null
                && teamMembers.countByTeamId(team.getId()) < tournament.getRosterSize()
                && team.getRemainingCredits() >= lot.getWinningBid();
        if (!placeable) {
            if (team != null) {
                log.warn("Lot {}: {} can no longer take {} at {}", lotId, team.getName(), player, lot.getWinningBid());
            }
            lot.setStatus(LotStatus.UNSOLD);
            lot.setWinningBid(0);
            lot.setWinningTeamId(null);
            note = player + ": unsold";
        } else {
            int price = lot.getWinningBid();
            team.setRemainingCredits(team.getRemainingCredits() - price);
            teams.saveAndFlush(team);
            teamMembers.saveAndFlush(new TeamMember(team, lot.getPlayerProfile(), price));
            lot.setStatus(LotStatus.SOLD);
            note = price == 0
                    ? "%s goes to %s (no bids)".formatted(player, team.getName())
                    : "%s sold to %s for %d".formatted(player, team.getName(), price);
        }

        lot.setEndsAt(null);
        lot.setPausedRemainingMs(null);
        lots.saveAndFlush(lot);

        if (lotId.equals(auction.getCurrentLotId())) {
            auction.setCurrentLotId(null);
        }
        // Sold or not, that nomination is done: the next team is up.
        advanceTurn(auction);
        auctions.saveAndFlush(auction);

        events.publishEvent(new AuctionEvents(auction.getId(), new AuctionMessage(
                AuctionMessage.Type.LOT_CLOSED,
                auction.getId(),
                auction.getStatus(),
                mapper.toLotView(lot, tournament),
                null,
                mapper.toTeamViews(tournament),
                note,
                Instant.now())));

        completeIfEverybodyIsPlaced(auction);
    }

    @Transactional
    public AuctionSnapshot updateSettings(Long auctionId, Integer lotDurationSeconds) {
        Auction auction = require(auctionId);
        if (lotDurationSeconds != null) {
            if (lotDurationSeconds < 5 || lotDurationSeconds > 600) {
                throw new BadRequestException("Lot duration must be between 5 and 600 seconds");
            }
            auction.setLotDurationSeconds(lotDurationSeconds);
        }
        auctions.saveAndFlush(auction);
        return mapper.toSnapshot(auction);
    }

    @Transactional
    public AuctionSnapshot complete(Long auctionId) {
        Auction auction = require(auctionId);
        requireNoOpenLot(auction);
        finish(auction, remainingLots(auctionId).size());
        return mapper.toSnapshot(auction);
    }

    /**
     * Ends the draft the moment there is nobody left to place - or nowhere left to put
     * them. Without this the last sale leaves the room sitting on a finished draft that
     * still calls itself live, waiting for an admin to press something that was never
     * wired up.
     */
    private void completeIfEverybodyIsPlaced(Auction auction) {
        if ((auction.getStatus() != AuctionStatus.LIVE && auction.getStatus() != AuctionStatus.PAUSED)
                || lots.existsByAuctionIdAndStatus(auction.getId(), LotStatus.OPEN)) {
            return;
        }
        int waiting = remainingLots(auction.getId()).size();
        if (waiting > 0 && !openSlots(auction.getTournament()).isEmpty()) {
            return;
        }
        finish(auction, waiting);
    }

    /** @param stranded players with no roster slot left to go to, normally none */
    private void finish(Auction auction, int stranded) {
        auction.setStatus(AuctionStatus.COMPLETE);
        auction.setCurrentLotId(null);
        auction.setTurnTeamId(null);
        auction.setPickLotId(null);
        auctions.saveAndFlush(auction);

        // The draft is what DRAFTING meant; with the rosters settled the tournament is on.
        Tournament tournament = auction.getTournament();
        tournament.setStatus(TournamentStatus.LIVE);
        tournaments.saveAndFlush(tournament);

        publishStatus(auction, stranded == 0
                ? "Draft complete"
                : "Draft complete. %d player(s) unplaced, rosters full"
                        .formatted(stranded));
    }

    /**
     * Players still waiting (never nominated, or nominated and nobody bid), in queue order:
     * unseen before unsold, then highest rank first, then whoever joined first. Sorted on
     * every read, so the order follows rank changes made after joining.
     */
    private List<Lot> remainingLots(Long auctionId) {
        Map<String, Integer> tiers = new HashMap<>();
        for (Rank rank : ranks.findAll()) {
            tiers.put(rank.getName().toLowerCase(Locale.ROOT), rank.getTier());
        }
        // No rank (or one we don't recognise) sorts below every real one.
        java.util.function.ToIntFunction<Lot> tier = lot -> {
            String name = lot.getPlayerProfile().getCurrentRank();
            return name == null ? -1 : tiers.getOrDefault(name.toLowerCase(Locale.ROOT), -1);
        };
        return lots.findByAuctionIdOrderBySeqAsc(auctionId).stream()
                .filter(lot -> lot.getStatus() == LotStatus.PENDING || lot.getStatus() == LotStatus.UNSOLD)
                .sorted(Comparator.comparing((Lot lot) -> lot.getStatus() == LotStatus.UNSOLD)
                        .thenComparing(Comparator.comparingInt(tier).reversed())
                        .thenComparingInt(Lot::getSeq))
                .toList();
    }

    /** One entry per empty roster slot, so a team with two gaps is twice as likely to draw. */
    private List<Team> openSlots(Tournament tournament) {
        Map<Long, Integer> rosterCounts = mapper.rosterCounts(tournament.getId());
        List<Team> slots = new ArrayList<>();
        for (Team team : teams.findByTournamentId(tournament.getId())) {
            int open = tournament.getRosterSize() - rosterCounts.getOrDefault(team.getId(), 0);
            for (int i = 0; i < open; i++) {
                slots.add(team);
            }
        }
        return slots;
    }

    /**
     * Every player needs a slot and every slot needs a player, or the draft ends with a
     * roster short and someone with nowhere to go. Counted in slots rather than whole
     * rosters because a captain already occupies one of their team's places.
     * <p>
     * Checked when the auction starts, while it is still cheap to add a player or a team.
     */
    /**
     * Sets the player limit from who actually turned up: everyone already seated (the
     * captains) plus everyone queued, divided across the teams and rounded up. When that
     * doesn't divide evenly some teams finish a player short; nobody is turned away, since
     * a team can always carry a substitute.
     */
    private void fitRosterSizeToPool(Auction auction, Tournament tournament, List<Team> roster) {
        Map<Long, Integer> rosterCounts = mapper.rosterCounts(tournament.getId());
        int seated = roster.stream().mapToInt(team -> rosterCounts.getOrDefault(team.getId(), 0)).sum();
        int most = roster.stream().mapToInt(team -> rosterCounts.getOrDefault(team.getId(), 0)).max().orElse(0);
        int waiting = remainingLots(auction.getId()).size();
        if (waiting == 0) {
            throw new BadRequestException("Nobody is in the queue yet.");
        }
        int size = Math.ceilDiv(seated + waiting, roster.size());
        // A team already holding more people than that (only from manual edits) keeps them.
        tournament.setRosterSize(Math.max(size, most));
        tournaments.saveAndFlush(tournament);
    }

    /** Every queued player needs somewhere to go. Only fails if teams were changed by hand. */
    private void requireRoomForEveryone(Auction auction, Tournament tournament, List<Team> roster) {
        int waiting = remainingLots(auction.getId()).size();
        int slots = openSlots(tournament).size();
        if (waiting > slots) {
            throw new BadRequestException(
                    "%d player(s) for only %d open slot(s) across %d team(s). Drop %d or add a team."
                            .formatted(waiting, slots, roster.size(), waiting - slots));
        }
    }

    private Auction require(Long auctionId) {
        return auctions.findById(auctionId)
                .orElseThrow(() -> NotFoundException.of("Auction", auctionId));
    }

    private java.util.Optional<Lot> currentLot(Auction auction) {
        return auction.getCurrentLotId() == null
                ? java.util.Optional.empty()
                : lots.findById(auction.getCurrentLotId());
    }

    private void requireLive(Auction auction) {
        if (auction.getStatus() != AuctionStatus.LIVE) {
            throw new BadRequestException("The auction is not live");
        }
    }

    private void requireNoOpenLot(Auction auction) {
        if (lots.existsByAuctionIdAndStatus(auction.getId(), LotStatus.OPEN)) {
            throw new BadRequestException("Close the current player before nominating another");
        }
    }

    private void publishStatus(Auction auction, String note) {
        events.publishEvent(new AuctionEvents(auction.getId(), new AuctionMessage(
                AuctionMessage.Type.STATUS_CHANGED,
                auction.getId(),
                auction.getStatus(),
                currentLot(auction).map(l -> mapper.toLotView(l, auction.getTournament())).orElse(null),
                null,
                mapper.toTeamViews(auction.getTournament()),
                note,
                Instant.now())));
    }
}
