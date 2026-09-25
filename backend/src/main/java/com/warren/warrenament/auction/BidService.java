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

    /**
     * Records a captain's sealed bid for the player on the block.
     * <p>
     * The amount is never broadcast - the room only learns that this team has locked in.
     * Captains may resubmit until the window closes; the reveal reads each team's last bid,
     * and the earlier ones stay in the audit trail.
     * <p>
     * The lot row is locked for the transaction so the "is everyone in?" count that decides
     * an early reveal cannot be run against a stale read.
     *
     * @return the bid as recorded, returned to its author alone
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

        if (amount < tournament.getMinBid()) {
            throw new BidRejectedException("Minimum bid is " + tournament.getMinBid());
        }

        int rosterCount = teamMembers.countByTeamId(team.getId());
        if (rosterCount >= tournament.getRosterSize()) {
            throw new BidRejectedException("Your roster is already full");
        }

        // The only ceiling is what the team actually holds. Spending it all is a legal,
        // and sometimes deliberate, way to play - see BudgetRules.
        int ceiling = BudgetRules.maxBid(team.getRemainingCredits(), rosterCount, tournament.getRosterSize());
        if (amount > ceiling) {
            throw new BidRejectedException("You only have " + ceiling + " credits left");
        }

        // Flush so the @Version bump is visible in the payload clients use for ordering.
        lots.saveAndFlush(lot);
        Bid bid = bids.saveAndFlush(new Bid(lotId, team.getId(), userId, amount));

        events.publishEvent(new AuctionEvents(auction.getId(), new AuctionMessage(
                AuctionMessage.Type.BID_LOCKED,
                auction.getId(),
                auction.getStatus(),
                mapper.toLotView(lot, tournament),
                // Deliberately no BidView: the amount is the whole secret.
                null,
                null,
                mapper.toTeamViews(tournament),
                team.getName() + " bid",
                Instant.now())));

        BidView view = new BidView(
                bid.getId(), lotId, team.getId(), team.getName(), amount, bid.getCreatedAt());

        // Nobody left to wait for: reveal now rather than making the room watch a dead clock.
        // Captains who cannot afford the floor, or whose roster is full, are not waited on.
        if (mapper.lockedInTeamIds(lot).size() >= mapper.captainsExpected(tournament)) {
            auctions.closeLot(lotId);
        }

        return view;
    }

    /** What this user may bid on the given lot, for disabling controls in the UI. */
    @Transactional(readOnly = true)
    public Map<String, Integer> bidLimits(Long tournamentId, Long lotId, Long userId) {
        Team team = teams.findByTournamentIdAndCaptainUserId(tournamentId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a captain in this tournament"));
        Tournament tournament = team.getTournament();
        lots.findById(lotId).orElseThrow(() -> NotFoundException.of("Lot", lotId));

        int rosterCount = teamMembers.countByTeamId(team.getId());
        return Map.of(
                "maxBid", BudgetRules.maxBid(
                        team.getRemainingCredits(), rosterCount, tournament.getRosterSize()),
                "minBid", tournament.getMinBid());
    }
}
