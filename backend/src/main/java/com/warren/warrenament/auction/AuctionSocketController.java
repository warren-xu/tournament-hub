package com.warren.warrenament.auction;

import com.warren.warrenament.auction.AuctionDtos.BidError;
import com.warren.warrenament.auction.AuctionDtos.PlaceBidCommand;
import com.warren.warrenament.auth.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Bids arrive here over STOMP. Everything else an admin does stays on REST, where plain
 * HTTP status codes and method security apply.
 */
@Controller
public class AuctionSocketController {

    private static final Logger log = LoggerFactory.getLogger(AuctionSocketController.class);

    private final BidService bids;
    private final SimpMessagingTemplate messaging;

    public AuctionSocketController(BidService bids, SimpMessagingTemplate messaging) {
        this.bids = bids;
        this.messaging = messaging;
    }

    @MessageMapping("/auction/{auctionId}/bid")
    public void bid(@DestinationVariable Long auctionId,
                    PlaceBidCommand command,
                    Principal principal) {
        AppUser user = appUser(principal);
        if (user == null) {
            return;
        }
        if (command == null || command.lotId() == null || command.amount() == null) {
            sendError(principal, null, "Malformed bid");
            return;
        }

        try {
            // Only a "locked in" notice is broadcast; the amount goes nowhere else.
            bids.submitBid(auctionId, command.lotId(), command.amount(), user.userId());
        } catch (RuntimeException ex) {
            // Rejections are routed back to the one bidder so the room is not spammed.
            sendError(principal, command.lotId(), ex.getMessage());
        }
    }

    private void sendError(Principal principal, Long lotId, String message) {
        messaging.convertAndSendToUser(
                principal.getName(), "/queue/errors", BidError.of(lotId, message));
    }

    private AppUser appUser(Principal principal) {
        if (principal instanceof Authentication auth
                && auth.getPrincipal() instanceof AppUser appUser) {
            return appUser;
        }
        log.warn("Rejected a bid from an unresolved principal: {}", principal);
        return null;
    }
}
