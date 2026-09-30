package com.warren.warrenament.auction;

import com.warren.warrenament.auction.AuctionDtos.AuctionSettingsRequest;
import com.warren.warrenament.auction.AuctionDtos.AuctionSnapshot;
import com.warren.warrenament.auction.AuctionDtos.NominateRequest;
import com.warren.warrenament.auth.AppUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin controls and the state snapshot. Bidding itself goes over STOMP - see
 * {@link AuctionSocketController}.
 */
@RestController
@RequestMapping("/api/auctions")
public class AuctionController {

    private final AuctionService auctions;
    private final BidService bids;

    public AuctionController(AuctionService auctions, BidService bids) {
        this.auctions = auctions;
        this.bids = bids;
    }

    /** Full room state. Clients call this on join and after a reconnect to resync. */
    @GetMapping("/{auctionId}")
    public AuctionSnapshot snapshot(@PathVariable Long auctionId) {
        return auctions.snapshot(auctionId);
    }

    /** The remaining queue, for the admin's nomination picker. */
    @GetMapping("/{auctionId}/queue")
    public java.util.List<AuctionDtos.LotView> queue(@PathVariable Long auctionId) {
        return auctions.queue(auctionId);
    }

    @GetMapping("/by-tournament/{tournamentId}")
    public AuctionSnapshot byTournament(@PathVariable Long tournamentId) {
        return auctions.snapshotByTournament(tournamentId);
    }

    @GetMapping("/{auctionId}/lots/{lotId}/limits")
    public Map<String, Integer> limits(@PathVariable Long auctionId,
                                       @PathVariable Long lotId,
                                       @RequestParam Long tournamentId,
                                       @AuthenticationPrincipal AppUser user) {
        return bids.bidLimits(tournamentId, lotId, user.userId());
    }

    @PostMapping("/tournaments/{tournamentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public AuctionSnapshot create(@PathVariable Long tournamentId) {
        return auctions.createForTournament(tournamentId);
    }

    @PostMapping("/{auctionId}/start")
    @PreAuthorize("hasRole('ADMIN')")
    public AuctionSnapshot start(@PathVariable Long auctionId) {
        return auctions.start(auctionId);
    }

    @PostMapping("/{auctionId}/pause")
    @PreAuthorize("hasRole('ADMIN')")
    public AuctionSnapshot pause(@PathVariable Long auctionId) {
        return auctions.pause(auctionId);
    }

    @PostMapping("/{auctionId}/resume")
    @PreAuthorize("hasRole('ADMIN')")
    public AuctionSnapshot resume(@PathVariable Long auctionId) {
        return auctions.resume(auctionId);
    }

    @PostMapping("/{auctionId}/nominate")
    @PreAuthorize("hasRole('ADMIN')")
    public AuctionSnapshot nominate(@PathVariable Long auctionId,
                                    @RequestBody NominateRequest request) {
        return request.playerProfileId() == null
                ? auctions.nominateNext(auctionId)
                : auctions.nominate(auctionId, request.playerProfileId());
    }

    /** Ends the current lot early instead of waiting for the timer. */
    /** The nominating captain's pick; the admin then opens bidding with /nominate. */
    @PostMapping("/{auctionId}/pick")
    public AuctionSnapshot pick(@PathVariable Long auctionId,
                                @RequestBody PickRequest request,
                                @AuthenticationPrincipal AppUser user) {
        return auctions.pickNomination(auctionId, user.userId(), request.playerProfileId());
    }

    public record PickRequest(Long playerProfileId) {
    }

    @PostMapping("/{auctionId}/lots/{lotId}/close")
    @PreAuthorize("hasRole('ADMIN')")
    public AuctionSnapshot closeLot(@PathVariable Long auctionId, @PathVariable Long lotId) {
        auctions.closeLot(lotId);
        return auctions.snapshot(auctionId);
    }

    @PutMapping("/{auctionId}/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public AuctionSnapshot settings(@PathVariable Long auctionId,
                                    @RequestBody AuctionSettingsRequest request) {
        return auctions.updateSettings(auctionId, request.lotDurationSeconds());
    }

    @PostMapping("/{auctionId}/complete")
    @PreAuthorize("hasRole('ADMIN')")
    public AuctionSnapshot complete(@PathVariable Long auctionId) {
        return auctions.complete(auctionId);
    }
}
