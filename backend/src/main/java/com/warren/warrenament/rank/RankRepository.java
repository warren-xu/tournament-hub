package com.warren.warrenament.rank;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RankRepository extends JpaRepository<Rank, Long> {

    List<Rank> findAllByOrderByTierAsc();

    List<Rank> findByActiveTrueOrderByTierAsc();

    Optional<Rank> findByTier(int tier);

    Optional<Rank> findByNameIgnoreCase(String name);
}
