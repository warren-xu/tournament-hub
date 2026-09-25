package com.warren.warrenament.auction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AuctionRepository extends JpaRepository<Auction, Long> {

    Optional<Auction> findByTournamentId(Long tournamentId);

    boolean existsByTournamentIdAndStatus(Long tournamentId, AuctionStatus status);

    /** Bulk delete; lots and bids go with it via the schema's cascades. */
    @Modifying
    @Query("delete from Auction a where a.tournament.id = :tournamentId")
    void deleteByTournamentIdInBulk(@Param("tournamentId") Long tournamentId);
}
