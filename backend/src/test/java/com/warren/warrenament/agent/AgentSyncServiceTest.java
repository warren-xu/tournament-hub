package com.warren.warrenament.agent;

import com.warren.warrenament.TestcontainersConfiguration;
import com.warren.warrenament.agent.AgentCatalog.CatalogAgent;
import com.warren.warrenament.agent.AgentDtos.AgentView;
import com.warren.warrenament.common.SyncResult;
import com.warren.warrenament.agent.AgentDtos.UpdateAgentRequest;
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

/** Exercises reconciliation against a stub roster - never touches the network. */
@SpringBootTest
@Import({TestcontainersConfiguration.class, AgentSyncServiceTest.StubCatalogConfig.class})
class AgentSyncServiceTest {

    /** Mutable stand-in for valorant-api.com. */
    static class StubCatalog implements AgentCatalog {
        List<CatalogAgent> agents = List.of();

        @Override
        public List<CatalogAgent> fetchAgents() {
            return agents;
        }
    }

    @TestConfiguration
    static class StubCatalogConfig {
        @Bean
        @Primary
        StubCatalog stubCatalog() {
            return new StubCatalog();
        }
    }

    @Autowired AgentSyncService sync;
    @Autowired AgentService service;
    @Autowired AgentRepository repository;
    @Autowired StubCatalog catalog;

    private static CatalogAgent agent(String id, String name, String role) {
        return new CatalogAgent(id, name, role, "https://icons.test/" + name + ".png",
                "https://icons.test/" + name + "-portrait.png");
    }

    @Test
    @DisplayName("the first sync adopts migration-seeded rows by name instead of duplicating them")
    void adoptsSeededRowsByName() {
        // Jett is already in the table from V2, with no external id.
        long before = repository.count();
        catalog.agents = List.of(agent("uuid-jett", "Jett", "Duelist"));

        SyncResult result = sync.sync();

        assertThat(result.added()).isZero();
        assertThat(result.updated()).isEqualTo(1);
        assertThat(repository.count()).isEqualTo(before);

        Agent jett = repository.findByNameIgnoreCase("Jett").orElseThrow();
        assertThat(jett.getExternalId()).isEqualTo("uuid-jett");
        assertThat(jett.getIconUrl()).isEqualTo("https://icons.test/Jett.png");
    }

    @Test
    @DisplayName("an agent the source adds shows up; running it again changes nothing")
    void addsNewAgentsAndIsIdempotent() {
        catalog.agents = List.of(agent("uuid-newbie", "Newbie", "Duelist"));

        assertThat(sync.sync().added()).isEqualTo(1);
        assertThat(repository.findByNameIgnoreCase("Newbie")).isPresent();

        SyncResult second = sync.sync();
        assertThat(second.added()).isZero();
        assertThat(second.updated()).isZero();
        assertThat(second.unchanged()).isEqualTo(1);
    }

    @Test
    @DisplayName("a rename upstream is followed, because matching is on uuid not name")
    void followsRenameViaExternalId() {
        catalog.agents = List.of(agent("uuid-rename", "OldName", "Sentinel"));
        sync.sync();

        catalog.agents = List.of(agent("uuid-rename", "NewName", "Sentinel"));
        SyncResult result = sync.sync();

        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.added()).isZero();
        assertThat(repository.findByNameIgnoreCase("OldName")).isEmpty();
        assertThat(repository.findByNameIgnoreCase("NewName")).isPresent();
    }

    @Test
    @DisplayName("a locally retired agent stays retired through a sync")
    void syncDoesNotUnretire() {
        catalog.agents = List.of(agent("uuid-benched", "Benched", "Controller"));
        sync.sync();

        AgentView created = service.findAll(true).stream()
                .filter(a -> a.name().equals("Benched")).findFirst().orElseThrow();
        service.update(created.id(),
                new UpdateAgentRequest("Benched", "Controller", false, null));

        sync.sync();

        assertThat(repository.findByNameIgnoreCase("Benched").orElseThrow().isActive())
                .isFalse();
    }

    @Test
    @DisplayName("an agent missing from the source is reported, never deleted")
    void reportsButKeepsAgentsMissingUpstream() {
        catalog.agents = List.of(agent("uuid-ghost", "Ghost", "Duelist"));
        sync.sync();

        // Upstream drops Ghost entirely.
        catalog.agents = List.of(agent("uuid-jett", "Jett", "Duelist"));
        SyncResult result = sync.sync();

        assertThat(result.notInSource()).contains("Ghost");
        assertThat(repository.findByNameIgnoreCase("Ghost")).isPresent();
    }

    @Test
    @DisplayName("an empty source is treated as a failure, not as 'delete everything'")
    void emptySourceIsANoop() {
        long before = repository.count();
        catalog.agents = List.of();

        SyncResult result = sync.sync();

        assertThat(result.added()).isZero();
        assertThat(result.note()).contains("no agents");
        assertThat(repository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("agents are ordered within their role, not globally")
    void ordersWithinRole() {
        catalog.agents = List.of(
                agent("u1", "Aaa", "Duelist"),
                agent("u2", "Bbb", "Duelist"),
                agent("u3", "Ccc", "Sentinel"));
        sync.sync();

        int aaa = repository.findByNameIgnoreCase("Aaa").orElseThrow().getDisplayOrder();
        int bbb = repository.findByNameIgnoreCase("Bbb").orElseThrow().getDisplayOrder();
        int ccc = repository.findByNameIgnoreCase("Ccc").orElseThrow().getDisplayOrder();

        assertThat(aaa).isLessThan(bbb);
        // Each role restarts its own numbering.
        assertThat(ccc).isEqualTo(aaa);
    }

    @Test
    @DisplayName("the measured portrait offset survives a sync")
    void syncLeavesPortraitFocusAlone() {
        // Jett's body sits well right of centre on Riot's canvas; V10 records that.
        float measured = repository.findByNameIgnoreCase("Jett").orElseThrow()
                .getPortraitFocusX();
        assertThat(measured).isNotEqualTo(0.5f);

        catalog.agents = List.of(agent("uuid-jett", "Jett", "Duelist"));
        sync.sync();

        assertThat(repository.findByNameIgnoreCase("Jett").orElseThrow().getPortraitFocusX())
                .isEqualTo(measured);
    }

    @Test
    @DisplayName("an agent the sync introduces starts dead centre")
    void newAgentDefaultsToCentre() {
        catalog.agents = List.of(agent("uuid-fresh", "Freshface", "Sentinel"));
        sync.sync();

        assertThat(repository.findByNameIgnoreCase("Freshface").orElseThrow()
                .getPortraitFocusX()).isEqualTo(0.5f);
    }
}