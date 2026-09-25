package com.warren.warrenament.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.warren.warrenament.common.Exceptions.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Reads the roster from the community-maintained valorant-api.com. */
@Component
public class ValorantApiAgentCatalog implements AgentCatalog {

    private static final Logger log = LoggerFactory.getLogger(ValorantApiAgentCatalog.class);

    private final RestClient restClient;
    private final String url;

    public ValorantApiAgentCatalog(
            @Value("${app.agents.source-url:https://valorant-api.com/v1/agents}") String url) {
        this.url = url;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        // A third party must never be able to hang an admin request indefinitely.
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        // Built directly rather than injecting RestClient.Builder: Boot 4 only
        // auto-configures that bean when a REST client starter is on the classpath.
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApiResponse(List<ApiAgent> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApiAgent(
            String uuid,
            String displayName,
            Boolean isPlayableCharacter,
            String displayIcon,
            String fullPortrait,
            ApiRole role) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApiRole(String displayName) {
    }

    @Override
    public List<CatalogAgent> fetchAgents() {
        ApiResponse response;
        try {
            response = restClient.get().uri(url).retrieve().body(ApiResponse.class);
        } catch (RestClientException ex) {
            log.warn("Agent sync failed calling {}", url, ex);
            throw new BadRequestException(
                    "Could not reach " + url + " — check the connection and try again.");
        }

        if (response == null || response.data() == null) {
            throw new BadRequestException("The agent source returned nothing usable.");
        }

        return response.data().stream()
                // The feed carries non-playable entries (it has shipped a duplicate Sova
                // before), and those have no role.
                .filter(a -> Boolean.TRUE.equals(a.isPlayableCharacter()))
                .filter(a -> a.role() != null && a.role().displayName() != null)
                .filter(a -> a.displayName() != null && !a.displayName().isBlank())
                .map(a -> new CatalogAgent(
                        a.uuid(), a.displayName().trim(), a.role().displayName().trim(),
                        a.displayIcon(), a.fullPortrait()))
                .sorted(Comparator.comparing(CatalogAgent::role)
                        .thenComparing(CatalogAgent::name, String.CASE_INSENSITIVE_ORDER))
                .filter(a -> Objects.nonNull(a.externalId()))
                .toList();
    }
}
