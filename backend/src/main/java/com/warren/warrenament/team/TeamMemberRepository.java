package com.warren.warrenament.team;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    List<TeamMember> findByTeamId(Long teamId);

    List<TeamMember> findByTournamentId(Long tournamentId);

    int countByTeamId(Long teamId);

    boolean existsByTournamentIdAndPlayerProfileId(Long tournamentId, Long playerProfileId);

    boolean existsByPlayerProfileId(Long playerProfileId);

    /** Most recent acquisition in the tournament - the sale an admin undo reverses. */
    java.util.Optional<TeamMember> findFirstByTournamentIdOrderByIdDesc(Long tournamentId);
}
