package com.warren.warrenament.tournament;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TournamentRepository extends JpaRepository<Tournament, Long> {

    Optional<Tournament> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /** Tournaments outlive the account that created them. */
    @Modifying
    @Transactional
    @Query("update Tournament t set t.createdByUserId = null where t.createdByUserId = :userId")
    void clearCreator(@Param("userId") Long userId);
}
