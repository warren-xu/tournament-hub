package com.warren.warrenament.auction;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BidRepository extends JpaRepository<Bid, Long> {

    List<Bid> findByLotIdOrderByIdAsc(Long lotId);

    /** The current round's bids: see {@link Lot#getOpenedAt()}. */
    List<Bid> findByLotIdAndCreatedAtGreaterThanEqualOrderByIdAsc(Long lotId, java.time.Instant since);

    boolean existsByUserId(Long userId);

    /** Detaches a departing user from their bids, keeping the audit trail intact. */
    @Modifying
    @Transactional
    @Query("update Bid b set b.userId = null where b.userId = :userId")
    void clearUser(@Param("userId") Long userId);
}
