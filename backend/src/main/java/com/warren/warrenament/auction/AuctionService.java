package com.warren.warrenament.auction;

import com.warren.warrenament.auction.AuctionDtos.AuctionMessage;
import com.warren.warrenament.auction.AuctionDtos.AuctionSnapshot;
import com.warren.warrenament.common.Exceptions.BadRequestException;
import com.warren.warrenament.common.Exceptions.NotFoundException;
import com.warren.warrenament.profile.PlayerProfile;
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

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Admin-driven lifecycle of an auction: build the queue, nominate, pause, close, undo. */
@Service
public class AuctionService {

    private static final Logger log = LoggerFactory.getLogger(AuctionService.class);

    /** Settles tied sealed bids and deals out the leftovers. Nothing here needs a seed. */
    private final Random random = new SecureRandom();

    private final AuctionRepository auctions;
    private final LotRepository lots;
    private final TournamentRepository tournaments;
    private final RegistrationRepository registrations;
    private final TeamRepository teams;
    private final TeamMemberRepository teamMembers;
    private final AuctionViewMapper mapper;
    private final ApplicationEventPublisher events;

    public AuctionService(AuctionRepository auctions,
                          LotRepository lots,
                          TournamentRepository tournaments,
                          RegistrationRepository registrations,
                          TeamRepository teams,
                          TeamMemberRepository teamMembers,
                          AuctionViewMapper mapper,
                          ApplicationEventPublisher events) {
        this.auctions = auctions;
        this.lots = lots;
        this.tournaments = tournaments;
        this.registrations = registrations;
        this.teams = teams;
        this.teamMembers = teamMembers;
        this.mapper = mapper;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public AuctionSnapshot snapshot(Long auctionId) {
        return mapper.toSnapshot(require(auctionId));
    }

    /** As {@link #snapshot}, but a captain also gets back their own sealed bid. */
    @Transactional(readOnly = true)
    public AuctionSnapshot snapshot(Long auctionId, Long viewerUserId) {
        return mapper.toSnapshot(require(auctionId), viewerUserId);
    }

    /**
     * Players still to place, in nomination order, so an admin can pick who goes up next.
     * <p>
     * Includes the ones who drew no bids: under sealed bidding a whole room can pass on
     * someone, and leaving them out of this list is what strands a draft with an empty
     * slot and nobody to put in it.
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
        return mapper.toSnapshot(auctions.save(new Auction(tournament)));
    }

    /**
     * Builds the lot queue from approved registrations that are not already on a roster.
     * Safe to re-run while in SETUP; it replaces any lots that have not been nominated yet.
     */
    @Transactional
    public AuctionSnapshot buildQueue(Long auctionId, boolean shuffle) {
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

        if (shuffle) {
            Collections.shuffle(pool);
        }

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
        requireSlotsMatchPool(auction, tournament, roster);
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
        Lot lot = lots.findFirstByAuctionIdAndStatusOrderBySeqAsc(auctionId, LotStatus.PENDING)
                // Nobody bid on them the first time; they go back up rather than vanish.
                .or(() -> lots.findFirstByAuctionIdAndStatusOrderBySeqAsc(auctionId, LotStatus.UNSOLD))
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
        return mapper.toSnapshot(auction, captainUserId);
    }

    /**
     * Hands the nomination to the next team, in creation order (Team 1, 2 ... n, then round
     * again), skipping teams whose roster is full. Clears any unused pick.
     */
    private void advanceTurn(Auction auction) {
        Tournament tournament = auction.getTournament();
        Map<Long, Integer> rosterCounts = mapper.rosterCounts(tournament.getId());
        List<Team> withRoom = teams.findByTournamentId(tournament.getId()).stream()
                .filter(t -> rosterCounts.getOrDefault(t.getId(), 0) < tournament.getRosterSize())
                .sorted(java.util.Comparator.comparing(Team::getId))
                .toList();

        auction.setPickLotId(null);
        if (withRoom.isEmpty()) {
            auction.setTurnTeamId(null);
            return;
        }
        Long current = auction.getTurnTeamId();
        Team next = withRoom.stream()
                .filter(t -> current == null || t.getId() > current)
                .findFirst()
                .orElse(withRoom.getFirst());
        auction.setTurnTeamId(next.getId());
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
        lot.setWinningBid(0);
        lot.setWinningTeamId(null);
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
                null,
                mapper.toTeamViews(auction.getTournament()),
                lot.getPlayerProfile().getUser().getUsername() + " is up",
                Instant.now())));

        return mapper.toSnapshot(auction);
    }

    /**
     * Reveals an expired lot and settles it.
     * <p>
     * Every captain's last sealed bid is opened at once; the highest takes the player at
     * exactly what they wrote. Equal top bids are settled by a coin flip, because there is
     * no bidding order to fall back on - the bids were never seen by anyone.
     * <p>
     * Idempotent: a lot that is no longer OPEN is left alone, so the sweeper, an early
     * "everyone is in" reveal and an admin can all call this without double-charging.
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
        Map<Long, String> teamNames = mapper.teamNames(tournament.getId());
        List<BidView> reveal = mapper.revealFor(lot, teamNames);
        String note;

        List<BidView> contenders = affordable(reveal, tournament);
        if (contenders.isEmpty()) {
            lot.setStatus(LotStatus.UNSOLD);
            lot.setWinningBid(0);
            lot.setWinningTeamId(null);
            note = reveal.isEmpty()
                    ? lot.getPlayerProfile().getUser().getUsername() + ": no bids"
                    : lot.getPlayerProfile().getUser().getUsername() + ": unsold";
        } else {
            int top = contenders.getFirst().amount();
            List<BidView> tied = contenders.stream().filter(b -> b.amount() == top).toList();
            BidView won = tied.size() == 1 ? tied.getFirst() : tied.get(random.nextInt(tied.size()));

            Team team = teams.findByIdForUpdate(won.teamId())
                    .orElseThrow(() -> NotFoundException.of("Team", won.teamId()));
            team.setRemainingCredits(team.getRemainingCredits() - top);
            teams.saveAndFlush(team);
            teamMembers.saveAndFlush(new TeamMember(team, lot.getPlayerProfile(), top));

            lot.setStatus(LotStatus.SOLD);
            lot.setWinningBid(top);
            lot.setWinningTeamId(team.getId());
            note = tied.size() == 1
                    ? "%s sold to %s for %d".formatted(
                            lot.getPlayerProfile().getUser().getUsername(), team.getName(), top)
                    : "%s sold to %s for %d (%d-way tie, random pick)".formatted(
                            lot.getPlayerProfile().getUser().getUsername(), team.getName(),
                            top, tied.size());
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
                reveal,
                mapper.toTeamViews(tournament),
                note,
                Instant.now())));

        dealOutRemainderIfNobodyCanBid(auction);
        completeIfEverybodyIsPlaced(auction);
    }

    /**
     * Bids their team can still honour, highest first.
     * <p>
     * Bidding already checks both of these, so a bid failing here means state moved under
     * it - a sale undone mid-lot, say - and dropping it beats voiding the whole sale.
     */
    private List<BidView> affordable(List<BidView> reveal, Tournament tournament) {
        Map<Long, Integer> rosterCounts = mapper.rosterCounts(tournament.getId());
        return reveal.stream()
                .filter(bid -> teams.findById(bid.teamId())
                        .map(team -> {
                            int roster = rosterCounts.getOrDefault(team.getId(), 0);
                            boolean ok = roster < tournament.getRosterSize()
                                    && team.getRemainingCredits() >= bid.amount();
                            if (!ok) {
                                log.warn("Dropping {}'s bid of {}: {} credits, roster {}/{}",
                                        team.getName(), bid.amount(), team.getRemainingCredits(),
                                        roster, tournament.getRosterSize());
                            }
                            return ok;
                        })
                        .orElse(false))
                .toList();
    }

    /**
     * Reverses the most recent sale in the tournament: refunds the team, removes the roster
     * entry and returns the player to the queue. The bid history is left intact.
     */
    @Transactional
    public AuctionSnapshot undoLastSale(Long auctionId) {
        Auction auction = require(auctionId);
        Tournament tournament = auction.getTournament();

        TeamMember member = teamMembers.findFirstByTournamentIdOrderByIdDesc(tournament.getId())
                .orElseThrow(() -> new BadRequestException("There are no sales to undo"));

        Team team = teams.findByIdForUpdate(member.getTeam().getId())
                .orElseThrow(() -> NotFoundException.of("Team", member.getTeam().getId()));
        team.setRemainingCredits(team.getRemainingCredits() + member.getPricePaid());
        teams.saveAndFlush(team);

        Long profileId = member.getPlayerProfile().getId();
        String username = member.getPlayerProfile().getUser().getUsername();
        teamMembers.delete(member);
        teamMembers.flush();

        lots.findByAuctionIdAndPlayerProfileId(auctionId, profileId).ifPresent(lot -> {
            lot.setStatus(LotStatus.PENDING);
            lot.setWinningBid(0);
            lot.setWinningTeamId(null);
            lot.setEndsAt(null);
            // Cleared so the next time this player goes up it is a fresh sealed round.
            lot.setOpenedAt(null);
            lot.setPausedRemainingMs(null);
            lots.saveAndFlush(lot);
        });

        if (auction.getStatus() == AuctionStatus.COMPLETE) {
            // There is a player to place again, so the draft is no longer over.
            auction.setStatus(AuctionStatus.LIVE);
            auctions.saveAndFlush(auction);
            tournament.setStatus(TournamentStatus.DRAFTING);
            tournaments.saveAndFlush(tournament);
        }

        publishStatus(auction, "Undid %s's sale to %s".formatted(username, team.getName()));
        return mapper.toSnapshot(auction);
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
     * Deals the players nobody can afford any more out at random, free of charge.
     * <p>
     * This is the safety net the "no maximum bid" rule leans on: a captain may empty the
     * budget on one star, and the cost of that is losing all say in who fills the rest of
     * the roster. It deliberately does not run while anyone can still bid - a team holding
     * credits has earned the right to use them.
     *
     * @throws BadRequestException if a captain could still bid, or nothing is left to deal
     */
    @Transactional
    public AuctionSnapshot fillRemainingRandomly(Long auctionId) {
        Auction auction = require(auctionId);
        if (bidderStillHoldingCredits(auction.getTournament())) {
            throw new BadRequestException(
                    "A captain can still bid. The random fill is only for players nobody has "
                            + "credits left for.");
        }
        if (remainingLots(auctionId).isEmpty()) {
            throw new BadRequestException("Every player has been dealt with");
        }
        int dealt = dealOutRemainder(auction);
        return dealt == 0 ? mapper.toSnapshot(auction) : mapper.toSnapshot(require(auctionId));
    }

    /** Runs the fill automatically the moment the last credit leaves the room. */
    private void dealOutRemainderIfNobodyCanBid(Auction auction) {
        if (auction.getStatus() != AuctionStatus.LIVE
                || bidderStillHoldingCredits(auction.getTournament())
                || remainingLots(auction.getId()).isEmpty()) {
            return;
        }
        dealOutRemainder(auction);
    }

    /**
     * Pairs the players left with the roster slots left, both shuffled, and hands each one
     * over at a price of zero. One message per assignment, in order, so the room can play
     * them out one at a time rather than having a finished bracket appear.
     *
     * @return how many players found a slot
     */
    private int dealOutRemainder(Auction auction) {
        Tournament tournament = auction.getTournament();
        List<Lot> waiting = new ArrayList<>(remainingLots(auction.getId()));
        List<Team> slots = openSlots(tournament);

        Collections.shuffle(waiting, random);
        Collections.shuffle(slots, random);

        int dealt = Math.min(waiting.size(), slots.size());
        for (int i = 0; i < dealt; i++) {
            Lot lot = waiting.get(i);
            Long teamId = slots.get(i).getId();
            Team team = teams.findByIdForUpdate(teamId)
                    .orElseThrow(() -> NotFoundException.of("Team", teamId));

            teamMembers.saveAndFlush(new TeamMember(team, lot.getPlayerProfile(), 0));
            lot.setStatus(LotStatus.SOLD);
            lot.setWinningBid(0);
            lot.setWinningTeamId(team.getId());
            lot.setEndsAt(null);
            lot.setPausedRemainingMs(null);
            lots.saveAndFlush(lot);

            events.publishEvent(new AuctionEvents(auction.getId(), new AuctionMessage(
                    AuctionMessage.Type.RANDOM_ASSIGNED,
                    auction.getId(),
                    auction.getStatus(),
                    mapper.toLotView(lot, tournament),
                    null,
                    null,
                    mapper.toTeamViews(tournament),
                    "%s to %s (random)".formatted(
                            lot.getPlayerProfile().getUser().getUsername(), team.getName()),
                    Instant.now())));
        }

        int stranded = waiting.size() - dealt;
        publishStatus(auction, stranded == 0
                ? "No credits left. %d player(s) assigned at random"
                        .formatted(dealt)
                : "%d player(s) assigned at random, %d unplaced"
                        .formatted(dealt, stranded));
        log.info("Auction {}: dealt {} players at random, {} stranded", auction.getId(), dealt, stranded);
        completeIfEverybodyIsPlaced(auction);
        return dealt;
    }

    /** Players still waiting: never nominated, or nominated and nobody bid. */
    private List<Lot> remainingLots(Long auctionId) {
        return lots.findByAuctionIdOrderBySeqAsc(auctionId).stream()
                .filter(lot -> lot.getStatus() == LotStatus.PENDING || lot.getStatus() == LotStatus.UNSOLD)
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

    private boolean bidderStillHoldingCredits(Tournament tournament) {
        Map<Long, Integer> rosterCounts = mapper.rosterCounts(tournament.getId());
        return teams.findByTournamentId(tournament.getId()).stream()
                .anyMatch(team -> BudgetRules.canBid(
                        team.getRemainingCredits(),
                        rosterCounts.getOrDefault(team.getId(), 0),
                        tournament.getRosterSize(),
                        tournament.getMinBid()));
    }

    /**
     * Every player needs a slot and every slot needs a player, or the draft ends with a
     * roster short and someone with nowhere to go. Counted in slots rather than whole
     * rosters because a captain already occupies one of their team's places.
     * <p>
     * Checked when the auction starts, while it is still cheap to add a player or a team.
     */
    private void requireSlotsMatchPool(Auction auction, Tournament tournament, List<Team> roster) {
        // Unsold players are still waiting for a slot, so they count towards the pool.
        int waiting = remainingLots(auction.getId()).size();
        int slots = openSlots(tournament).size();
        if (waiting == slots) {
            return;
        }
        throw new BadRequestException(waiting < slots
                ? "%d player(s) for %d open slot(s) across %d team(s). Queue %d more."
                        .formatted(waiting, slots, roster.size(), slots - waiting)
                : "%d player(s) for only %d open slot(s) across %d team(s). Drop %d or add a team."
                        .formatted(waiting, slots, roster.size(), waiting - slots));
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
                null,
                mapper.toTeamViews(auction.getTournament()),
                note,
                Instant.now())));
    }
}
