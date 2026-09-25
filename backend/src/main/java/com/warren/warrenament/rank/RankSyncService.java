package com.warren.warrenament.rank;

import com.warren.warrenament.common.SyncResult;
import com.warren.warrenament.rank.RankCatalog.CatalogRank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Same contract as the agent sync: add and update only, never delete, and never
 * overwrite the local {@code active} flag.
 */
@Service
public class RankSyncService {

    private static final Logger log = LoggerFactory.getLogger(RankSyncService.class);

    private final RankRepository ranks;
    private final RankCatalog catalog;

    public RankSyncService(RankRepository ranks, RankCatalog catalog) {
        this.ranks = ranks;
        this.catalog = catalog;
    }

    @Transactional
    public SyncResult sync() {
        List<CatalogRank> incoming = catalog.fetchRanks();
        if (incoming.isEmpty()) {
            return SyncResult.noop("The source returned no ranks.");
        }

        int added = 0;
        int updated = 0;
        int unchanged = 0;
        Set<Integer> seenTiers = new HashSet<>();

        for (CatalogRank incomingRank : incoming) {
            seenTiers.add(incomingRank.tier());

            Rank rank = ranks.findByTier(incomingRank.tier()).orElse(null);

            if (rank == null) {
                Rank created = new Rank(
                        incomingRank.tier(), incomingRank.name(), incomingRank.division());
                created.setColor(incomingRank.color());
                created.setIconUrl(incomingRank.iconUrl());
                ranks.save(created);
                added++;
                continue;
            }

            boolean changed = !rank.getName().equals(incomingRank.name())
                    || !rank.getDivision().equals(incomingRank.division())
                    || !Objects.equals(rank.getColor(), incomingRank.color())
                    || !Objects.equals(rank.getIconUrl(), incomingRank.iconUrl());

            if (changed) {
                rank.setName(incomingRank.name());
                rank.setDivision(incomingRank.division());
                rank.setColor(incomingRank.color());
                rank.setIconUrl(incomingRank.iconUrl());
                ranks.save(rank);
                updated++;
            } else {
                unchanged++;
            }
        }

        List<String> notInSource = ranks.findAllByOrderByTierAsc().stream()
                .filter(r -> !seenTiers.contains(r.getTier()))
                .map(Rank::getName)
                .toList();

        log.info("Rank sync: {} added, {} updated, {} unchanged, {} not in source",
                added, updated, unchanged, notInSource.size());

        return new SyncResult(added, updated, unchanged, notInSource, null);
    }
}
