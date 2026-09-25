package com.warren.warrenament.playercard;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PlayerCardRepository extends JpaRepository<PlayerCard, Long> {
    List<PlayerCard> findAllByOrderByNameAscIdAsc();
    Optional<PlayerCard> findByExternalId(String externalId);
}
