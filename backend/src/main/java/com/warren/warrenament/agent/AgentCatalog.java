package com.warren.warrenament.agent;

import java.util.List;

/**
 * Source of the canonical agent roster. Behind an interface so the sync logic can be
 * tested without reaching the network.
 */
public interface AgentCatalog {

    /**
     * @param externalId Riot's agent uuid - stable across renames
     */
    record CatalogAgent(String externalId, String name, String role, String iconUrl,
                        String portraitUrl) {
    }

    List<CatalogAgent> fetchAgents();
}
