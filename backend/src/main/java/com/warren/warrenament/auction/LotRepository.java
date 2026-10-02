package com.warren.warrenament.auction;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LotRepository extends JpaRepository<Lot, Long> {

    List<Lot> findByAuctionIdOrderBySeqAsc(Long auctionId);

    List<Lot> findByAuctionIdAndStatus(Long auctionId, LotStatus status);

    boolean existsByAuctionIdAndStatus(Long auctionId, LotStatus status);

    long countByAuctionIdAndStatus(Long auctionId, LotStatus status);

    Optional<Lot> findByAuctionIdAndPlayerProfileId(Long auctionId, Long playerProfileId);

    List<Lot> findByPlayerProfileId(Long playerProfileId);

    @Query("select coalesce(max(l.seq), 0) from Lot l where l.auction.id = :auctionId")
    int maxSeq(@Param("auctionId") Long auctionId);

    /**
     * Serialises bidders on a single lot. Every bid and every close goes through this,
     * so concurrent bids are applied one at a time rather than racing on a read-modify-write.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lot l where l.id = :id")
    Optional<Lot> findByIdForUpdate(@Param("id") Long id);

    /** Feeds the sweeper. Returns ids only so the close can take its own lock per lot. */
    @Query("select l.id from Lot l where l.status = com.warren.warrenament.auction.LotStatus.OPEN "
            + "and l.endsAt is not null and l.endsAt <= :now")
    List<Long> findExpiredOpenLotIds(@Param("now") Instant now);
}
