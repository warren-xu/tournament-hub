package com.warren.warrenament.auction;

import com.warren.warrenament.auction.AuctionDtos.BidView;
import com.warren.warrenament.auction.AuctionDtos.AuctionSnapshot;
import com.warren.warrenament.auction.AuctionDtos.LotView;
import com.warren.warrenament.auction.AuctionDtos.PlayerSummary;
import com.warren.warrenament.auction.AuctionDtos.RosterEntry;
import com.warren.warrenament.auction.AuctionDtos.TeamView;
import com.warren.warrenament.auth.UserRepository;
import com.warren.warrenament.team.Team;
import com.warren.warrenament.team.TeamMember;
import com.warren.warrenament.team.TeamMemberRepository;
import com.warren.warrenament.team.TeamRepository;
import com.warren.warrenament.tournament.Tournament;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Turns entities into the payloads the auction room renders. */
@Component
public class AuctionViewMapper {

    private final TeamRepository teams;
    private final TeamMemberRepository teamMembers;
    private final BidRepository bids;
    private final LotRepository lots;
    private final UserRepository users;

    public AuctionViewMapper(TeamRepository teams,
                             TeamMemberRepository teamMembers,
                             BidRepository bids,
                             LotRepository lots,
                             UserRepository users) {
        this.teams = teams;
        this.teamMembers = teamMembers;
        this.bids = bids;
        this.lots = lots;
        this.users = users;
    }

    public LotView toLotView(Lot lot, Tournament tournament) {
        if (lot == null) {
            return null;
        }
        String winnerName = lot.getWinningTeamId() == null ? null
                : teams.findById(lot.getWinningTeamId()).map(Team::getName).orElse(null);

        return new LotView(
                lot.getId(),
                lot.getSeq(),
                pickNumber(lot),
                lot.getStatus(),
                PlayerSummary.of(lot.getPlayerProfile()),
                lot.getWinningBid(),
                lot.getWinningTeamId(),
                winnerName,
                nextMinimum(lot, tournament),
                lot.getEndsAt(),
                lot.getVersion());
    }

    /** A sold lot's pick; an open one would be the next pick if it sells. */
    private Integer pickNumber(Lot lot) {
        return switch (lot.getStatus()) {
            case SOLD -> lot.getPickNumber();
            case OPEN -> (int) lots.countByAuctionIdAndStatus(lot.getAuction().getId(), LotStatus.SOLD) + 1;
            default -> null;
        };
    }

    /**
     * The lowest bid that would take the lead: one credit over the current price, or the
     * tournament's floor while nobody holds the player (no nominating team to start them).
     */
    public int nextMinimum(Lot lot, Tournament tournament) {
        return lot.getWinningTeamId() == null
                ? Math.max(1, tournament.getMinBid())
                : lot.getWinningBid() + 1;
    }

    /** A lot put up again (it went unsold) starts clean; earlier rounds stay in the audit trail. */
    public List<Bid> bidsThisRound(Lot lot) {
        return lot.getOpenedAt() == null
                ? bids.findByLotIdOrderByIdAsc(lot.getId())
                : bids.findByLotIdAndCreatedAtGreaterThanEqualOrderByIdAsc(lot.getId(), lot.getOpenedAt());
    }

    public Map<Long, Integer> rosterCounts(Long tournamentId) {
        Map<Long, Integer> counts = new java.util.HashMap<>();
        for (TeamMember member : teamMembers.findByTournamentId(tournamentId)) {
            counts.merge(member.getTeam().getId(), 1, Integer::sum);
        }
        return counts;
    }

    public List<TeamView> toTeamViews(Tournament tournament) {
        List<TeamMember> allMembers = teamMembers.findByTournamentId(tournament.getId());
        Map<Long, List<TeamMember>> byTeam = allMembers.stream()
                .collect(Collectors.groupingBy(m -> m.getTeam().getId()));

        return teams.findByTournamentId(tournament.getId()).stream()
                .map(team -> {
                    List<TeamMember> members = byTeam.getOrDefault(team.getId(), List.of());
                    List<RosterEntry> roster = members.stream()
                            .map(m -> new RosterEntry(
                                    m.getPlayerProfile().getId(),
                                    m.getPlayerProfile().getUser().getUsername(),
                                    m.getPricePaid()))
                            .toList();
                    return new TeamView(
                            team.getId(),
                            team.getName(),
                            team.getLogoUrl(),
                            team.getCaptainUserId(),
                            users.findById(team.getCaptainUserId())
                                    .map(u -> u.getUsername()).orElse(null),
                            team.getRemainingCredits(),
                            members.size(),
                            tournament.getRosterSize(),
                            BudgetRules.maxBid(
                                    team.getRemainingCredits(),
                                    members.size(),
                                    tournament.getRosterSize()),
                            roster);
                })
                .toList();
    }

    public BidView toBidView(Bid bid, Map<Long, String> teamNames) {
        return new BidView(
                bid.getId(),
                bid.getLotId(),
                bid.getTeamId(),
                teamNames.get(bid.getTeamId()),
                bid.getAmount(),
                bid.getCreatedAt());
    }

    public AuctionSnapshot toSnapshot(Auction auction) {
        Tournament tournament = auction.getTournament();
        Lot currentLot = auction.getCurrentLotId() == null ? null
                : lots.findById(auction.getCurrentLotId()).orElse(null);

        Map<Long, String> teamNames = teamNames(tournament.getId());

        List<BidView> recentBids = currentLot == null ? List.of() : bidHistory(currentLot, teamNames);

        // Players still to place. An unsold player is still to come, not done with.
        int pending = (int) lots.findByAuctionIdOrderBySeqAsc(auction.getId()).stream()
                .filter(l -> l.getStatus() == LotStatus.PENDING || l.getStatus() == LotStatus.UNSOLD)
                .count();

        return new AuctionSnapshot(
                auction.getId(),
                tournament.getId(),
                auction.getStatus(),
                toLotView(currentLot, tournament),
                toTeamViews(tournament),
                recentBids,
                pending,
                auction.getTurnTeamId(),
                auction.getPickLotId() == null ? null : lots.findById(auction.getPickLotId())
                        .map(lot -> PlayerSummary.of(lot.getPlayerProfile())).orElse(null),
                Instant.now());
    }

    /** This round's bids on a lot, newest (and so highest) first. */
    public List<BidView> bidHistory(Lot lot, Map<Long, String> teamNames) {
        List<BidView> history = new java.util.ArrayList<>(
                bidsThisRound(lot).stream().map(bid -> toBidView(bid, teamNames)).toList());
        java.util.Collections.reverse(history);
        return history;
    }

    public Map<Long, String> teamNames(Long tournamentId) {
        return teams.findByTournamentId(tournamentId).stream()
                .collect(Collectors.toMap(Team::getId, Team::getName));
    }
}
