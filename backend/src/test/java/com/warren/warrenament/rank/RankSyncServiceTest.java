package com.warren.warrenament.rank;

import com.warren.warrenament.TestcontainersConfiguration;
import com.warren.warrenament.common.SyncResult;
import com.warren.warrenament.rank.RankCatalog.CatalogRank;
import com.warren.warrenament.rank.RankDtos.RankView;
import com.warren.warrenament.rank.RankDtos.UpdateRankRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({TestcontainersConfiguration.class, RankSyncServiceTest.StubCatalogConfig.class})
class RankSyncServiceTest {

    static class StubCatalog implements RankCatalog {
        List<CatalogRank> ranks = List.of();

        @Override
        public List<CatalogRank> fetchRanks() {
            return ranks;
        }
    }

    @TestConfiguration
    static class StubCatalogConfig {
        @Bean
        @Primary
        StubCatalog stubRankCatalog() {
            return new StubCatalog();
        }
    }

    @Autowired RankSyncService sync;
    @Autowired RankService service;
    @Autowired RankRepository repository;
    @Autowired StubCatalog catalog;

    private static CatalogRank rank(int tier, String name, String division) {
        return new CatalogRank(tier, name, division, "868986ff",
                "https://icons.test/" + tier + ".png");
    }

    @Test
    @DisplayName("the seeded tiers cover Iron 1 through Radiant in ascending order")
    void seededTiersAreOrdered() {
        List<RankView> all = service.findAll(false);

        assertThat(all).extracting(RankView::name)
                .contains("Iron 1", "Ascendant 3", "Immortal 1", "Radiant");
        assertThat(all).extracting(RankView::tier).isSorted();
        // V5 backfills icons and Riot's tier colours, so the picker works on first run.
        // Asserted on a seeded tier specifically, since the database is shared with the
        // other tests in this class.
        assertThat(all).filteredOn(r -> r.name().equals("Iron 1"))
                .singleElement()
                .matches(r -> r.iconUrl() != null && r.color() != null,
                        "has a seeded icon and colour");
        // Radiant is the top tier, so it sorts last.
        assertThat(all).last().extracting(RankView::name).isEqualTo("Radiant");
    }

    @Test
    @DisplayName("sync fills icons and colours onto the migration-seeded tiers")
    void syncEnrichesSeededTiers() {
        long before = repository.count();
        catalog.ranks = List.of(rank(3, "Iron 1", "Iron"));

        SyncResult result = sync.sync();

        assertThat(result.added()).isZero();
        assertThat(result.updated()).isEqualTo(1);
        assertThat(repository.count()).isEqualTo(before);

        Rank iron = repository.findByTier(3).orElseThrow();
        assertThat(iron.getIconUrl()).isEqualTo("https://icons.test/3.png");
        assertThat(iron.getColor()).isEqualTo("868986ff");
    }

    @Test
    @DisplayName("a brand new tier is added, and re-running changes nothing")
    void addsNewTiersAndIsIdempotent() {
        catalog.ranks = List.of(rank(28, "Mythic 1", "Mythic"));

        assertThat(sync.sync().added()).isEqualTo(1);
        assertThat(repository.findByTier(28)).isPresent();

        SyncResult second = sync.sync();
        assertThat(second.added()).isZero();
        assertThat(second.updated()).isZero();
        assertThat(second.unchanged()).isEqualTo(1);
    }

    @Test
    @DisplayName("a tier hidden locally stays hidden through a sync")
    void syncDoesNotUnhide() {
        RankView radiant = service.findAll(true).stream()
                .filter(r -> r.name().equals("Radiant")).findFirst().orElseThrow();
        service.update(radiant.id(), new UpdateRankRequest("Radiant", false));

        catalog.ranks = List.of(rank(27, "Radiant", "Radiant"));
        sync.sync();

        assertThat(repository.findByTier(27).orElseThrow().isActive()).isFalse();
        assertThat(service.findAll(false)).noneMatch(r -> r.name().equals("Radiant"));
    }

    @Test
    @DisplayName("tiers the source omits are reported, not deleted")
    void reportsButKeepsTiersMissingUpstream() {
        catalog.ranks = List.of(rank(3, "Iron 1", "Iron"));

        SyncResult result = sync.sync();

        assertThat(result.notInSource()).contains("Radiant", "Immortal 3");
        assertThat(repository.findByTier(27)).isPresent();
    }

    @Test
    @DisplayName("an empty source is a no-op, not a wipe")
    void emptySourceIsANoop() {
        long before = repository.count();
        catalog.ranks = List.of();

        SyncResult result = sync.sync();

        assertThat(result.note()).contains("no ranks");
        assertThat(repository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("Riot's shouty tier names are title-cased for display")
    void titleCasesNames() {
        assertThat(ValorantApiRankCatalog.titleCase("IRON 1")).isEqualTo("Iron 1");
        assertThat(ValorantApiRankCatalog.titleCase("RADIANT")).isEqualTo("Radiant");
        assertThat(ValorantApiRankCatalog.titleCase("UNRANKED")).isEqualTo("Unranked");
    }
}
