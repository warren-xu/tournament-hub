package com.warren.warrenament.auction;

import com.warren.warrenament.auction.AuctionDtos.BidView;
import com.warren.warrenament.auction.AuctionDtos.AuctionSnapshot;
import com.warren.warrenament.auction.AuctionDtos.LotView;
import com.warren.warrenament.auction.AuctionDtos.PlayerSummary;
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
                lot.getStatus(),
                PlayerSummary.of(lot.getPlayerProfile()),
                lot.getWinningBid(),
                lot.getWinningTeamId(),
                winnerName,
                tournament.getMinBid(),
                lockedInTeamIds(lot),
                captainsExpected(tournament),
                lot.getStatus() == LotStatus.SOLD && lot.getWinningBid() == 0,
                lot.getEndsAt(),
                lot.getVersion());
    }

    /**
     * Which teams have a sealed bid on this lot. Who has committed is public - the room
     * wants to see the last captain hesitating - while what they committed is not.
     */
    public List<Long> lockedInTeamIds(Lot lot) {
        return bidsThisRound(lot).stream()
                .map(Bid::getTeamId)
                .distinct()
                .toList();
    }

    /** A lot put up again after an undo starts clean; earlier rounds stay in the audit trail. */
    public List<Bid> bidsThisRound(Lot lot) {
        return lot.getOpenedAt() == null
                ? bids.findByLotIdOrderByIdAsc(lot.getId())
                : bids.findByLotIdAndCreatedAtGreaterThanEqualOrderByIdAsc(lot.getId(), lot.getOpenedAt());
    }

    /** Captains who could still bid, and so are worth waiting for before a reveal. */
    public int captainsExpected(Tournament tournament) {
        Map<Long, Integer> rosterCounts = rosterCounts(tournament.getId());
        return (int) teams.findByTournamentId(tournament.getId()).stream()
                .filter(team -> BudgetRules.canBid(
                        team.getRemainingCredits(),
                        rosterCounts.getOrDefault(team.getId(), 0),
                        tournament.getRosterSize(),
                        tournament.getMinBid()))
                .count();
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
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
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
        return toSnapshot(auction, null);
    }

    /**
     * @param viewerUserId the captain asking, so their own sealed bid can be returned to
     *                     them - a refresh mid-lot should not lose what they locked in
     */
    public AuctionSnapshot toSnapshot(Auction auction, Long viewerUserId) {
        Tournament tournament = auction.getTournament();
        Lot currentLot = auction.getCurrentLotId() == null ? null
                : lots.findById(auction.getCurrentLotId()).orElse(null);

        Map<Long, String> teamNames = teamNames(tournament.getId());

        // Amounts stay sealed until the lot closes; an open lot reveals nothing.
        List<BidView> recentBids = currentLot == null || currentLot.getStatus() == LotStatus.OPEN
                ? List.of()
                : revealFor(currentLot, teamNames);

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
                yourBid(currentLot, tournament.getId(), viewerUserId),
                pending,
                auction.getTurnTeamId(),
                auction.getPickLotId() == null ? null : lots.findById(auction.getPickLotId())
                        .map(lot -> PlayerSummary.of(lot.getPlayerProfile())).orElse(null),
                Instant.now());
    }

    /** Each team's final sealed bid on a closed lot, biggest first. */
    public List<BidView> revealFor(Lot lot, Map<Long, String> teamNames) {
        Map<Long, Bid> lastPerTeam = new java.util.LinkedHashMap<>();
        for (Bid bid : bidsThisRound(lot)) {
            lastPerTeam.put(bid.getTeamId(), bid);
        }
        return lastPerTeam.values().stream()
                .map(bid -> toBidView(bid, teamNames))
                .sorted((a, b) -> Integer.compare(b.amount(), a.amount()))
                .toList();
    }

    private Integer yourBid(Lot lot, Long tournamentId, Long viewerUserId) {
        if (lot == null || viewerUserId == null) {
            return null;
        }
        return teams.findByTournamentIdAndCaptainUserId(tournamentId, viewerUserId)
                .flatMap(team -> bidsThisRound(lot).stream()
                        .filter(bid -> bid.getTeamId().equals(team.getId()))
                        .reduce((first, second) -> second))
                .map(Bid::getAmount)
                .orElse(null);
    }

    public Map<Long, String> teamNames(Long tournamentId) {
        return teams.findByTournamentId(tournamentId).stream()
                .collect(Collectors.toMap(Team::getId, Team::getName));
    }
}
