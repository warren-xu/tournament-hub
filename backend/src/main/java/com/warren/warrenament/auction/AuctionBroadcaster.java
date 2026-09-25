package com.warren.warrenament.auction;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends auction updates to subscribers after the database transaction commits, so viewers
 * never see a bid that then rolls back.
 */
@Component
public class AuctionBroadcaster {

    public static String topic(Long auctionId) {
        return "/topic/auction/" + auctionId;
    }

    private final SimpMessagingTemplate messaging;

    public AuctionBroadcaster(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAuctionEvent(AuctionEvents event) {
        messaging.convertAndSend(topic(event.auctionId()), event.message());
    }
}
