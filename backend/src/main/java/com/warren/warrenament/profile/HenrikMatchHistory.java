package com.warren.warrenament.profile;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.warren.warrenament.common.Exceptions.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads matches and ranks from the community HenrikDev API (api.henrikdev.xyz), which
 * needs a free key from its dashboard in {@code HENRIK_API_KEY}.
 */
@Component
public class HenrikMatchHistory implements MatchHistory {

    private static final Logger log = LoggerFactory.getLogger(HenrikMatchHistory.class);

    /** HenrikDev's own error codes, found in the body of a 404. */
    private static final Pattern ERROR_CODE = Pattern.compile("\"code\"\\s*:\\s*(\\d+)");
    private static final int ACCOUNT_NOT_FOUND = 22;
    /** 23 "Region not found" and 24 "needed match data": a real account, but no recent game. */
    private static final Set<Integer> NO_RECENT_GAMES = Set.of(23, 24);

    private final RestClient restClient;
    private final String apiKey;

    public HenrikMatchHistory(
            @Value("${app.henrik.base-url:https://api.henrikdev.xyz}") String baseUrl,
            @Value("${app.henrik.api-key:}") String apiKey) {
        this.apiKey = apiKey;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        // A third party must never be able to hang a profile save indefinitely.
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", apiKey)
                .build();
    }

    // Only the fields the import reads; the real payloads are far larger.

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApiAccountResponse(ApiAccount data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApiAccount(String puuid, String name, String tag, String region, String card) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApiMatchesResponse(List<ApiMatch> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApiMatch(ApiMetadata metadata, List<ApiPlayer> players) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApiMetadata(@JsonProperty("match_id") String matchId, ApiQueue queue) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApiQueue(String id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApiPlayer(String puuid, ApiIdName agent, ApiTier tier) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApiIdName(String id, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApiTier(Integer id, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApiMmrResponse(ApiMmr data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApiMmr(ApiRating current, ApiRating peak) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApiRating(ApiTier tier) {
    }

    @Override
    public Optional<Account> account(String name, String tag) {
        requireKey();
        try {
            ApiAccountResponse response = restClient.get()
                    .uri("/valorant/v2/account/{name}/{tag}", name, tag)
                    .retrieve().body(ApiAccountResponse.class);
            return Optional.ofNullable(response).map(ApiAccountResponse::data)
                    .filter(a -> a.puuid() != null && a.region() != null)
                    .map(a -> new Account(a.puuid(), a.name(), a.tag(), a.region().toLowerCase(), a.card()));
        } catch (HttpClientErrorException ex) {
            failOnAccountProblem(ex);
            Integer code = errorCode(ex);
            if (code != null && NO_RECENT_GAMES.contains(code)) {
                throw new BadRequestException("That Riot ID has no recent games on record yet. "
                        + "Play any match, even a Deathmatch, then try again in a few minutes.");
            }
            if (code != null && code != ACCOUNT_NOT_FOUND) {
                log.info("HenrikDev account lookup returned code {}: {}", code, ex.getMessage());
            }
            return Optional.empty();
        } catch (RestClientException ex) {
            log.warn("HenrikDev account lookup failed", ex);
            throw new BadRequestException("Couldn't reach the stats service. Try again in a minute.");
        }
    }

    @Override
    public Optional<Rating> rating(String affinity, String puuid) {
        try {
            ApiMmrResponse response = restClient.get()
                    .uri("/valorant/v3/by-puuid/mmr/{affinity}/pc/{puuid}", affinity, puuid)
                    .retrieve().body(ApiMmrResponse.class);
            return Optional.ofNullable(response).map(ApiMmrResponse::data)
                    .map(mmr -> new Rating(tierOf(mmr.current()), tierOf(mmr.peak())));
        } catch (RestClientException ex) {
            // The latest competitive match still carries a rank, so this is not fatal.
            log.info("HenrikDev rank lookup failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<Match> recentCompetitiveMatches(String affinity, String puuid, int count) {
        try {
            ApiMatchesResponse response = restClient.get()
                    .uri("/valorant/v4/by-puuid/matches/{affinity}/pc/{puuid}?mode=competitive&size={size}",
                            affinity, puuid, count)
                    .retrieve().body(ApiMatchesResponse.class);
            if (response == null || response.data() == null) {
                return List.of();
            }
            return response.data().stream()
                    .filter(Objects::nonNull)
                    .map(m -> toMatch(m, affinity))
                    .toList();
        } catch (RestClientException ex) {
            log.info("HenrikDev match history lookup failed: {}", ex.getMessage());
            return List.of();
        }
    }

    private static Integer tierOf(ApiRating rating) {
        return rating == null || rating.tier() == null ? null : rating.tier().id();
    }

    private static Integer errorCode(HttpClientErrorException ex) {
        Matcher m = ERROR_CODE.matcher(ex.getResponseBodyAsString());
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }

    private void requireKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BadRequestException("Match import isn't set up on this server yet.");
        }
    }

    private static void failOnAccountProblem(HttpClientErrorException ex) {
        if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED || ex.getStatusCode() == HttpStatus.FORBIDDEN) {
            log.warn("HenrikDev rejected the API key: {}", ex.getMessage());
            throw new BadRequestException("Match import isn't set up correctly on this server.");
        }
        if (ex.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
            throw new BadRequestException("Too many imports right now. Try again in a minute.");
        }
    }

    private static Match toMatch(ApiMatch match, String affinity) {
        List<MatchPlayer> players = match.players() == null ? List.of() : match.players().stream()
                .filter(p -> p != null && p.puuid() != null)
                .map(p -> new MatchPlayer(
                        p.puuid(),
                        p.agent() == null ? null : p.agent().name(),
                        p.tier() == null || p.tier().id() == null ? 0 : p.tier().id()))
                .toList();
        String id = match.metadata() == null ? null : match.metadata().matchId();
        String queue = match.metadata() == null || match.metadata().queue() == null
                ? null : match.metadata().queue().id();
        return new Match(id, affinity, queue, players);
    }
}
