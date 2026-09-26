package com.warren.warrenament.playercard;

import com.warren.warrenament.TestFixtures;
import com.warren.warrenament.TestcontainersConfiguration;
import com.warren.warrenament.common.Exceptions.BadRequestException;
import com.warren.warrenament.profile.PlayerProfileDtos.UpdateProfileRequest;
import com.warren.warrenament.profile.PlayerProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PlayerCardCatalogTest.CatalogConfig.class})
@Transactional
class PlayerCardCatalogTest {
    static class StubCatalog implements PlayerCardCatalog {
        List<CatalogCard> data = List.of();
        int calls;
        boolean fail;
        public List<CatalogCard> fetchCards() {
            calls++;
            if (fail) throw new BadRequestException("Source unavailable");
            return data;
        }
    }

    @TestConfiguration
    static class CatalogConfig {
        @Bean @Primary StubCatalog cardCatalog() { return new StubCatalog(); }
    }

    @Autowired StubCatalog catalog;
    @Autowired PlayerCardService cards;
    @Autowired PlayerCardRepository repository;
    @Autowired PlayerProfileService profiles;
    @Autowired TestFixtures fixtures;
    @Autowired MockMvc mvc;

    @BeforeEach
    void resetSource() {
        catalog.data = List.of(new PlayerCardCatalog.CatalogCard("card-uuid", "First Card", "small.png", "large.png"));
        catalog.calls = 0;
        catalog.fail = false;
    }

    @Test
    void syncAddsUpdatesAndRetainsStableIds() {
        assertThat(cards.sync().added()).isEqualTo(1);
        var original = repository.findByExternalId("card-uuid").orElseThrow();
        Long id = original.getId();
        assertThat(cards.sync().unchanged()).isEqualTo(1);
        catalog.data = List.of(new PlayerCardCatalog.CatalogCard("card-uuid", "Renamed Card", "small.png", "new.png"));
        assertThat(cards.sync().updated()).isEqualTo(1);
        var updated = repository.findByExternalId("card-uuid").orElseThrow();
        assertThat(updated.getId()).isEqualTo(id);
        assertThat(updated.getLargeArt()).isEqualTo("new.png");
        assertThat(updated.getName()).isEqualTo("Renamed Card");
    }

    @Test
    void missingEmptyAndFailedSourcesKeepSavedCards() {
        cards.sync();
        catalog.data = List.of(new PlayerCardCatalog.CatalogCard("second", "Other Card", null, "portrait.png"));
        assertThat(cards.sync().notInSource()).contains("First Card");
        catalog.data = List.of();
        assertThat(cards.sync().note()).contains("no player cards");
        catalog.fail = true;
        assertThatThrownBy(cards::sync).isInstanceOf(BadRequestException.class);
        assertThat(repository.findByExternalId("card-uuid")).isPresent();
        assertThat(repository.findByExternalId("second")).isPresent();
    }

    @Test
    void syncSkipsCardsWithoutPortraitsAndPreservesExistingArtwork() {
        cards.sync();
        catalog.data = List.of(
                new PlayerCardCatalog.CatalogCard("card-uuid", "First Card", "small.png", null),
                new PlayerCardCatalog.CatalogCard("wide-only", "Wide Only", "small.png", null),
                new PlayerCardCatalog.CatalogCard("blank", "Blank", null, " "),
                new PlayerCardCatalog.CatalogCard("portrait", "Portrait", null, "portrait.png"));
        assertThat(cards.sync().added()).isEqualTo(1);
        assertThat(repository.findByExternalId("wide-only")).isEmpty();
        assertThat(repository.findByExternalId("blank")).isEmpty();
        assertThat(repository.findByExternalId("card-uuid").orElseThrow().getLargeArt()).isEqualTo("large.png");
        assertThat(cards.findAll()).allMatch(c -> c.largeArt() != null && !c.largeArt().isBlank());
    }

    @Test
    void selectionSurvivesReloadRejectsUnknownIdsAndClears() {
        cards.sync();
        Long id = repository.findByExternalId("card-uuid").orElseThrow().getId();
        Long userId = fixtures.profile("card-selection").getUser().getId();
        profiles.updateMine(userId, request(id));
        assertThat(profiles.findMine(userId).playerCard().id()).isEqualTo(id);
        assertThatThrownBy(() -> profiles.updateMine(userId, request(Long.MAX_VALUE)))
                .isInstanceOf(BadRequestException.class);
        assertThat(profiles.findMine(userId).playerCard().id()).isEqualTo(id);
        profiles.updateMine(userId, request(null));
        assertThat(profiles.findMine(userId).playerCard()).isNull();
    }

    @Test
    void publicReadsDoNotCallUpstream() throws Exception {
        cards.sync();
        catalog.calls = 0;
        catalog.fail = true;
        mvc.perform(get("/api/player-cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("First Card"));
        cards.findAll();
        assertThat(catalog.calls).isZero();
    }

    @Test
    @WithMockUser(roles = "PLAYER")
    void playersCannotSync() throws Exception {
        mvc.perform(post("/api/player-cards/sync")).andExpect(status().isForbidden());
        assertThat(catalog.calls).isZero();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminsCanSync() throws Exception {
        mvc.perform(post("/api/player-cards/sync"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.added").value(1));
        assertThat(catalog.calls).isEqualTo(1);
    }

    private UpdateProfileRequest request(Long cardId) {
        return new UpdateProfileRequest("card#test", null, null, null, null, Set.of(), null, cardId, null);
    }
}
