package com.warren.warrenament.tournament;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {

    List<Registration> findByTournamentId(Long tournamentId);

    List<Registration> findByTournamentIdAndStatus(Long tournamentId, RegistrationStatus status);

    Optional<Registration> findByTournamentIdAndPlayerProfileId(Long tournamentId, Long playerProfileId);

    boolean existsByTournamentIdAndPlayerProfileId(Long tournamentId, Long playerProfileId);
}
