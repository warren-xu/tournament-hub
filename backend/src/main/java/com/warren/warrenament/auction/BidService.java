package com.warren.warrenament.auction;

import com.warren.warrenament.auction.AuctionDtos.AuctionMessage;
import com.warren.warrenament.auction.AuctionDtos.BidView;
import com.warren.warrenament.common.Exceptions.BadRequestException;
import com.warren.warrenament.common.Exceptions.BidRejectedException;
import com.warren.warrenament.common.Exceptions.ForbiddenException;
import com.warren.warrenament.common.Exceptions.NotFoundException;
import com.warren.warrenament.team.Team;
import com.warren.warrenament.team.TeamMemberRepository;
import com.warren.warrenament.team.TeamRepository;
import com.warren.warrenament.tournament.Tournament;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
public class BidService {

    private final AuctionService auctions;
    private final LotRepository lots;
    private final BidRepository bids;
    private final TeamRepository teams;
    private final TeamMemberRepository teamMembers;
    private final AuctionViewMapper mapper;
    private final ApplicationEventPublisher events;

    public BidService(AuctionService auctions,
                      LotRepository lots,
                      BidRepository bids,
                      TeamRepository teams,
                      TeamMemberRepository teamMembers,
                      AuctionViewMapper mapper,
                      ApplicationEventPublisher events) {
        this.auctions = auctions;
        this.lots = lots;
        this.bids = bids;
        this.teams = teams;
        this.teamMembers = teamMembers;
        this.mapper = mapper;
        this.events = events;
    }

    /** Every bid gives the room at least this long to answer before the lot closes. */
    static final int BID_RESET_SECONDS = 10;

    /**
     * Places a live bid: it must beat the current price by at least one credit, and the
     * team holding the player can't bid against itself. The new price, leader and
     * deadline go to the whole room at once.
     * <p>
     * Each bid restarts a short countdown ({@link #BID_RESET_SECONDS}) without ever
     * shortening the clock. The lot row is locked for the transaction, so two captains
     * bidding in the same moment are applied one after the other and the second is checked
     * against the price the first one set.
     *
     * @return the bid as recorded
     * @throws BidRejectedException when the bid is understood but not allowed
     */
    @Transactional
    public BidView submitBid(Long auctionId, Long lotId, int amount, Long userId) {
        Lot lot = lots.findByIdForUpdate(lotId)
                .orElseThrow(() -> NotFoundException.of("Lot", lotId));

        Auction auction = lot.getAuction();
        if (!auction.getId().equals(auctionId)) {
            throw new BadRequestException("Lot " + lotId + " is not part of auction " + auctionId);
        }
        if (auction.getStatus() != AuctionStatus.LIVE) {
            throw new BidRejectedException("The auction is not accepting bids right now");
        }
        if (lot.getStatus() != LotStatus.OPEN) {
            throw new BidRejectedException("Bidding on this player has closed");
        }

        Instant now = Instant.now();
        if (lot.getEndsAt() == null || !now.isBefore(lot.getEndsAt())) {
            throw new BidRejectedException("Time is up for this player");
        }

        Tournament tournament = auction.getTournament();
        Team team = teams.findByTournamentIdAndCaptainUserId(tournament.getId(), userId)
                .orElseThrow(() -> new ForbiddenException("You are not a captain in this tournament"));

        if (team.getId().equals(lot.getWinningTeamId())) {
            throw new BidRejectedException("You already hold this player");
        }
        int floor = mapper.nextMinimum(lot, tournament);
        if (amount < floor) {
            throw new BidRejectedException("Bid at least " + floor);
        }

        int rosterCount = teamMembers.countByTeamId(team.getId());
        if (rosterCount >= tournament.getRosterSize()) {
            throw new BidRejectedException("Your roster is already full");
        }

        // The only ceiling is what the team actually holds - see BudgetRules.
        int ceiling = BudgetRules.maxBid(team.getRemainingCredits(), rosterCount, tournament.getRosterSize());
        if (amount > ceiling) {
            throw new BidRejectedException("You only have " + ceiling + " credits left");
        }

        lot.setWinningBid(amount);
        lot.setWinningTeamId(team.getId());
        Instant reset = now.plusSeconds(BID_RESET_SECONDS);
        if (lot.getEndsAt().isBefore(reset)) {
            lot.setEndsAt(reset);
        }
        // Flush so the @Version bump is visible in the payload clients use for ordering.
        lots.saveAndFlush(lot);
        Bid bid = bids.saveAndFlush(new Bid(lotId, team.getId(), userId, amount));

        BidView view = new BidView(
                bid.getId(), lotId, team.getId(), team.getName(), amount, bid.getCreatedAt());
        events.publishEvent(new AuctionEvents(auction.getId(), new AuctionMessage(
                AuctionMessage.Type.BID_PLACED,
                auction.getId(),
                auction.getStatus(),
                mapper.toLotView(lot, tournament),
                view,
                mapper.toTeamViews(tournament),
                "%s bid %d".formatted(team.getName(), amount),
                Instant.now())));

        // Nobody left who could beat this: settle now rather than make the room watch a dead clock.
        auctions.closeIfUncontested(lot);
        return view;
    }

    /** What this user may bid on the given lot, for disabling controls in the UI. */
    @Transactional(readOnly = true)
    public Map<String, Integer> bidLimits(Long tournamentId, Long lotId, Long userId) {
        Team team = teams.findByTournamentIdAndCaptainUserId(tournamentId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a captain in this tournament"));
        Tournament tournament = team.getTournament();
        Lot lot = lots.findById(lotId).orElseThrow(() -> NotFoundException.of("Lot", lotId));

        int rosterCount = teamMembers.countByTeamId(team.getId());
        return Map.of(
                "maxBid", BudgetRules.maxBid(
                        team.getRemainingCredits(), rosterCount, tournament.getRosterSize()),
                "minBid", mapper.nextMinimum(lot, tournament));
    }
}
