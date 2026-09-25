package com.warren.warrenament.rank;

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
import java.util.Locale;

@Component
public class ValorantApiRankCatalog implements RankCatalog {

    private static final Logger log = LoggerFactory.getLogger(ValorantApiRankCatalog.class);

    private final RestClient restClient;
    private final String url;

    public ValorantApiRankCatalog(
            @Value("${app.ranks.source-url:https://valorant-api.com/v1/competitivetiers}")
            String url) {
        this.url = url;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApiResponse(List<ApiTierSet> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApiTierSet(String uuid, String assetObjectName, List<ApiTier> tiers) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApiTier(
            Integer tier,
            String tierName,
            String division,
            String divisionName,
            String color,
            String smallIcon,
            String largeIcon) {
    }

    @Override
    public List<CatalogRank> fetchRanks() {
        ApiResponse response;
        try {
            response = restClient.get().uri(url).retrieve().body(ApiResponse.class);
        } catch (RestClientException ex) {
            log.warn("Rank sync failed calling {}", url, ex);
            throw new BadRequestException(
                    "Could not reach " + url + " — check the connection and try again.");
        }

        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new BadRequestException("The rank source returned nothing usable.");
        }

        // The feed carries every episode's tier table. Only the last one is current —
        // Ascendant, for instance, does not exist in the earlier sets.
        ApiTierSet current = response.data().get(response.data().size() - 1);
        if (current.tiers() == null) {
            throw new BadRequestException("The current tier set had no tiers.");
        }

        return current.tiers().stream()
                .filter(t -> t.tier() != null && t.tierName() != null)
                // Riot pads the table with Unused1/Unused2 placeholders.
                .filter(t -> t.division() == null
                        || !t.division().endsWith("INVALID"))
                .filter(t -> t.smallIcon() != null)
                .map(t -> new CatalogRank(
                        t.tier(),
                        titleCase(t.tierName()),
                        titleCase(t.divisionName()),
                        t.color(),
                        t.smallIcon()))
                .sorted(Comparator.comparingInt(CatalogRank::tier))
                .toList();
    }

    /** Riot ships these shouting: "IRON 1" -> "Iron 1". */
    static String titleCase(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String[] words = value.trim().toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
