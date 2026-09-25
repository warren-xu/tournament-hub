package com.warren.warrenament.team;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findByTournamentId(Long tournamentId);

    Optional<Team> findByTournamentIdAndCaptainUserId(Long tournamentId, Long captainUserId);

    boolean existsByTournamentIdAndCaptainUserId(Long tournamentId, Long captainUserId);

    boolean existsByCaptainUserId(Long captainUserId);

    /**
     * Locked read used when settling a won lot, so two lots closing at once cannot
     * both spend the same credits.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Team t where t.id = :id")
    Optional<Team> findByIdForUpdate(@Param("id") Long id);
}
