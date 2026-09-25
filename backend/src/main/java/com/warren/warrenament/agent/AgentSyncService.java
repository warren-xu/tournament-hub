package com.warren.warrenament.agent;

import com.warren.warrenament.agent.AgentCatalog.CatalogAgent;
import com.warren.warrenament.common.SyncResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pulls the roster from {@link AgentCatalog} and reconciles it into the agents table.
 * <p>
 * Deliberately never deletes: an agent the source no longer lists is reported rather
 * than removed, because player cards store the agent name and would keep showing it.
 * The local {@code active} flag is also preserved — retiring an agent is a tournament
 * decision, not something an upstream sync should undo.
 */
@Service
public class AgentSyncService {

    private static final Logger log = LoggerFactory.getLogger(AgentSyncService.class);

    private final AgentRepository agents;
    private final AgentCatalog catalog;

    public AgentSyncService(AgentRepository agents, AgentCatalog catalog) {
        this.agents = agents;
        this.catalog = catalog;
    }

    @Transactional
    public SyncResult sync() {
        List<CatalogAgent> incoming = catalog.fetchAgents();
        if (incoming.isEmpty()) {
            return SyncResult.noop("The source returned no agents.");
        }

        List<Agent> existing = agents.findAll();
        Map<String, Agent> byExternalId = new HashMap<>();
        Map<String, Agent> byName = new HashMap<>();
        for (Agent agent : existing) {
            if (agent.getExternalId() != null) {
                byExternalId.put(agent.getExternalId(), agent);
            }
            byName.put(agent.getName().toLowerCase(), agent);
        }

        int added = 0;
        int updated = 0;
        int unchanged = 0;
        List<String> seenNames = new ArrayList<>();
        Map<String, Integer> orderByRole = new HashMap<>();

        for (CatalogAgent incomingAgent : incoming) {
            int order = orderByRole.merge(incomingAgent.role(), 10, Integer::sum);
            seenNames.add(incomingAgent.name().toLowerCase());

            // Match on uuid first; fall back to name so rows seeded by the migration
            // are adopted rather than duplicated.
            Agent agent = Optional.ofNullable(byExternalId.get(incomingAgent.externalId()))
                    .orElseGet(() -> byName.get(incomingAgent.name().toLowerCase()));

            if (agent == null) {
                Agent created = new Agent(incomingAgent.name(), incomingAgent.role(), order);
                created.setExternalId(incomingAgent.externalId());
                created.setIconUrl(incomingAgent.iconUrl());
                created.setPortraitUrl(incomingAgent.portraitUrl());
                agents.save(created);
                added++;
                continue;
            }

            boolean changed = !agent.getName().equals(incomingAgent.name())
                    || !agent.getRole().equals(incomingAgent.role())
                    || !java.util.Objects.equals(agent.getIconUrl(), incomingAgent.iconUrl())
                    || !java.util.Objects.equals(agent.getPortraitUrl(), incomingAgent.portraitUrl())
                    || !java.util.Objects.equals(agent.getExternalId(), incomingAgent.externalId())
                    || agent.getDisplayOrder() != order;

            if (changed) {
                agent.setName(incomingAgent.name());
                agent.setRole(incomingAgent.role());
                agent.setIconUrl(incomingAgent.iconUrl());
                agent.setPortraitUrl(incomingAgent.portraitUrl());
                agent.setExternalId(incomingAgent.externalId());
                agent.setDisplayOrder(order);
                agents.save(agent);
                updated++;
            } else {
                unchanged++;
            }
        }

        // Anything we hold that upstream no longer lists. Surfaced, never deleted.
        List<String> notInSource = existing.stream()
                .filter(a -> !seenNames.contains(a.getName().toLowerCase()))
                .map(Agent::getName)
                .sorted()
                .toList();

        log.info("Agent sync: {} added, {} updated, {} unchanged, {} not in source",
                added, updated, unchanged, notInSource.size());

        return new SyncResult(added, updated, unchanged, notInSource, null);
    }
}
