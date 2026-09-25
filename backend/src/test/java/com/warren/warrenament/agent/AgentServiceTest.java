package com.warren.warrenament.agent;

import com.warren.warrenament.TestcontainersConfiguration;
import com.warren.warrenament.agent.AgentDtos.AgentView;
import com.warren.warrenament.agent.AgentDtos.CreateAgentRequest;
import com.warren.warrenament.agent.AgentDtos.UpdateAgentRequest;
import com.warren.warrenament.common.Exceptions.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AgentServiceTest {

    @Autowired AgentService service;

    @Test
    @DisplayName("the migration seeds a roster across all four roles")
    void seededRosterIsUsable() {
        List<AgentView> all = service.findAll(false);

        assertThat(all).isNotEmpty();
        assertThat(all).extracting(AgentView::role).contains(
                "Duelist", "Initiator", "Controller", "Sentinel");
        assertThat(all).allMatch(AgentView::active);
        // V5 backfills icons, so a fresh database renders portraits before any sync.
        // Asserted on a seeded agent specifically: other tests in this class add
        // icon-less rows, and the context (and database) is shared across them.
        assertThat(all).filteredOn(a -> a.name().equals("Jett"))
                .singleElement()
                .matches(a -> a.iconUrl() != null && a.iconUrl().startsWith("https://"),
                        "has a seeded icon")
                .matches(a -> a.portraitUrl() != null
                                && a.portraitUrl().endsWith("/fullportrait.png"),
                        "has seeded full-body art");
    }

    @Test
    @DisplayName("a new agent is appended to the end of its role group")
    void createAppendsWithinRole() {
        AgentView created = service.create(new CreateAgentRequest("Testbot", "Duelist"));

        List<AgentView> duelists = service.findAll(false).stream()
                .filter(a -> a.role().equals("Duelist"))
                .toList();

        assertThat(duelists).last().isEqualTo(created);
        assertThat(created.displayOrder())
                .isGreaterThan(duelists.get(0).displayOrder());
    }

    @Test
    @DisplayName("agent names are unique regardless of casing")
    void rejectsDuplicateNames() {
        service.create(new CreateAgentRequest("Duplicate", "Sentinel"));

        assertThatThrownBy(() -> service.create(new CreateAgentRequest("duplicate", "Duelist")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already an agent");
    }

    @Test
    @DisplayName("renaming to a name another agent already holds is rejected")
    void rejectsRenameCollision() {
        AgentView first = service.create(new CreateAgentRequest("Alpha", "Duelist"));
        service.create(new CreateAgentRequest("Beta", "Duelist"));

        assertThatThrownBy(() ->
                service.update(first.id(), new UpdateAgentRequest("Beta", "Duelist", null, null)))
                .isInstanceOf(BadRequestException.class);

        // Renaming to its own name is not a collision.
        assertThat(service.update(first.id(),
                new UpdateAgentRequest("Alpha", "Initiator", null, null)).role())
                .isEqualTo("Initiator");
    }

    @Test
    @DisplayName("a retired agent disappears from the picker but survives in the table")
    void retireHidesWithoutDeleting() {
        AgentView agent = service.create(new CreateAgentRequest("Retiree", "Controller"));

        service.update(agent.id(), new UpdateAgentRequest("Retiree", "Controller", false, null));

        assertThat(service.findAll(false)).noneMatch(a -> a.name().equals("Retiree"));
        assertThat(service.findAll(true))
                .filteredOn(a -> a.name().equals("Retiree"))
                .singleElement()
                .matches(a -> !a.active());
    }
}
