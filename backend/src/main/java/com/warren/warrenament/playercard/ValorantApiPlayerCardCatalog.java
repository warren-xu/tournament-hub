package com.warren.warrenament.playercard;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.warren.warrenament.common.Exceptions.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;

@Component
public class ValorantApiPlayerCardCatalog implements PlayerCardCatalog {
    private final RestClient client;
    private final String url;

    public ValorantApiPlayerCardCatalog(
            @Value("${app.player-cards.source-url:https://valorant-api.com/v1/playercards}") String url) {
        this.url = url;
        var factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApiResponse(List<ApiCard> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApiCard(String uuid, String displayName, String smallArt, String largeArt) {}

    @Override
    public List<CatalogCard> fetchCards() {
        ApiResponse response;
        try {
            response = client.get().uri(url).retrieve().body(ApiResponse.class);
        } catch (RestClientException ex) {
            throw new BadRequestException("Could not reach the player card source. Try syncing again later.");
        }
        if (response == null || response.data() == null) {
            throw new BadRequestException("The player card source returned nothing usable.");
        }
        return response.data().stream()
                .filter(c -> c != null && c.uuid() != null && !c.uuid().isBlank())
                .filter(c -> c.displayName() != null && !c.displayName().isBlank())
                .map(c -> new CatalogCard(c.uuid(), c.displayName().trim(), c.smallArt(), c.largeArt()))
                .toList();
    }
}
