package com.warren.warrenament.auction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Closes lots whose timer has run out.
 * <p>
 * A polling sweeper is used rather than a scheduled task per lot because it survives a
 * restart: if the backend goes down mid-draft, the lot still closes correctly on the way
 * back up instead of staying open forever.
 */
@Component
public class LotSweeper {

    private static final Logger log = LoggerFactory.getLogger(LotSweeper.class);

    private final LotRepository lots;
    private final AuctionService auctions;

    public LotSweeper(LotRepository lots, AuctionService auctions) {
        this.lots = lots;
        this.auctions = auctions;
    }

    @Scheduled(fixedDelayString = "${app.auction.sweep-interval-ms:500}")
    public void closeExpiredLots() {
        List<Long> expired = lots.findExpiredOpenLotIds(Instant.now());
        for (Long lotId : expired) {
            try {
                // Each close takes its own lock, so one failure cannot stall the others.
                auctions.closeLot(lotId);
            } catch (RuntimeException ex) {
                log.error("Failed to close expired lot {}", lotId, ex);
            }
        }
    }
}
