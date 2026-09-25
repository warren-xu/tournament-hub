package com.warren.warrenament.auction;

import com.warren.warrenament.auction.AuctionDtos.AuctionMessage;

/**
 * Published inside the transaction that changed the auction, delivered by
 * {@link AuctionBroadcaster} only once that transaction commits.
 */
public record AuctionEvents(Long auctionId, AuctionMessage message) {
}
