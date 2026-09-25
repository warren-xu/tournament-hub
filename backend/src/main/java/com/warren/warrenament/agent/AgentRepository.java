package com.warren.warrenament.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AgentRepository extends JpaRepository<Agent, Long> {

    List<Agent> findAllByOrderByRoleAscDisplayOrderAscNameAsc();

    List<Agent> findByActiveTrueOrderByRoleAscDisplayOrderAscNameAsc();

    Optional<Agent> findByNameIgnoreCase(String name);

    @Query("select coalesce(max(a.displayOrder), 0) from Agent a where a.role = :role")
    int maxDisplayOrder(@Param("role") String role);
}
