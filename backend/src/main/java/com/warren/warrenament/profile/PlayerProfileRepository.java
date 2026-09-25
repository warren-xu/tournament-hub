package com.warren.warrenament.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlayerProfileRepository extends JpaRepository<PlayerProfile, Long> {

    Optional<PlayerProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}
