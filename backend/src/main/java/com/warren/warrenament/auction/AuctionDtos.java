package com.warren.warrenament.auction;

import com.warren.warrenament.profile.PlayerProfile;

import java.time.Instant;
import java.util.List;

public final class AuctionDtos {

    private AuctionDtos() {
    }

    public record PlayerSummary(
            Long profileId,
            String username,
            String avatarUrl,
            String riotId,
            String currentRank,
            String primaryRole,
            String secondaryRole
    ) {
        public static PlayerSummary of(PlayerProfile p) {
            return new PlayerSummary(
                    p.getId(),
                    p.getUser().getUsername(),
                    p.getUser().getAvatarUrl(),
                    p.getRiotId(),
                    p.getCurrentRank(),
                    p.getPrimaryRole(),
                    p.getSecondaryRole());
        }
    }

    /**
     * A player up for (or done with) live bidding. While OPEN, {@code winningBid} and the
     * winning team are the current price and who holds it - the nominating team at 0 until
     * someone bids; once closed they're the result.
     */
    public record LotView(
            Long lotId,
            int seq,
            LotStatus status,
            PlayerSummary player,
            int winningBid,
            Long winningTeamId,
            String winningTeamName,
            /** The lowest bid accepted right now: one over the current price. */
            int minBid,
            Instant endsAt,
            long version
    ) {
    }

    public record RosterEntry(Long profileId, String username, int pricePaid) {
    }

    public record TeamView(
            Long teamId,
            String name,
            String logoUrl,
            Long captainUserId,
            String captainUsername,
            int remainingCredits,
            int rosterCount,
            int rosterSize,
            int maxBid,
            List<RosterEntry> roster
    ) {
    }

    public record BidView(
            Long bidId,
            Long lotId,
            Long teamId,
            String teamName,
            int amount,
            Instant createdAt
    ) {
    }

    /**
     * Full state of an auction room. Sent on join and on reconnect so a client that missed
     * messages can resync without replaying history.
     */
    public record AuctionSnapshot(
            Long auctionId,
            Long tournamentId,
            AuctionStatus status,
            LotView currentLot,
            List<TeamView> teams,
            /** Bids on the player up now, newest first; empty between players. */
            List<BidView> recentBids,
            int pendingLots,
            /** The team whose captain nominates next, or null outside a running draft. */
            Long turnTeamId,
            /** That captain's pick, waiting for the admin to open bidding; null until they choose. */
            PlayerSummary pickedPlayer,
            Instant serverTime
    ) {
    }

    /** Envelope broadcast to {@code /topic/auction/{id}}. Unused fields are null. */
    public record AuctionMessage(
            Type type,
            Long auctionId,
            AuctionStatus status,
            LotView lot,
            BidView bid,
            List<TeamView> teams,
            String note,
            Instant serverTime
    ) {
        public enum Type {
            LOT_OPENED,
            /** A new high bid: {@code lot} carries the new price, leader and deadline. */
            BID_PLACED,
            LOT_CLOSED,
            STATUS_CHANGED,
            SNAPSHOT
        }

        public static AuctionMessage of(Type type, Long auctionId) {
            return new AuctionMessage(
                    type, auctionId, null, null, null, null, null, Instant.now());
        }
    }

    /** Sent by a captain over {@code /app/auction/{id}/bid}: a new high bid. */
    public record PlaceBidCommand(Long lotId, Integer amount) {
    }

    /** Delivered to the offending user only, on {@code /user/queue/errors}. */
    public record BidError(Long lotId, String message, Instant serverTime) {
        public static BidError of(Long lotId, String message) {
            return new BidError(lotId, message, Instant.now());
        }
    }

    public record NominateRequest(Long playerProfileId) {
    }

    public record AuctionSettingsRequest(Integer lotDurationSeconds) {
    }
}
