package com.warren.warrenament.profile;

import com.warren.warrenament.TestFixtures;
import com.warren.warrenament.TestcontainersConfiguration;
import com.warren.warrenament.common.Exceptions.BadRequestException;
import com.warren.warrenament.profile.PlayerProfileDtos.UpdateProfileRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PlayerCardTest {
    @Autowired TestFixtures fixtures;
    @Autowired PlayerProfileService service;

    @Test
    void mainAgentAndRankSurviveReloadAndCanBeCleared() {
        Long userId = fixtures.profile("card-player").getUser().getId();
        service.updateMine(userId, request("Jett", Set.of("Jett", "Sage")));
        var saved = service.findMine(userId);
        assertThat(saved.mainAgent()).isEqualTo("Jett");
        assertThat(saved.currentRank()).isEqualTo("Gold 2");
        assertThat(saved.agents()).containsExactlyInAnyOrder("Jett", "Sage");

        service.updateMine(userId, request(null, Set.of("Sage")));
        assertThat(service.findMine(userId).mainAgent()).isNull();
    }

    @Test
    void rejectsMainOutsidePoolWithoutChangingSavedCard() {
        Long userId = fixtures.profile("invalid-card-player").getUser().getId();
        service.updateMine(userId, request("Sage", Set.of("Sage")));
        assertThatThrownBy(() -> service.updateMine(userId, request("Jett", Set.of("Sage"))))
                .isInstanceOf(BadRequestException.class);
        assertThat(service.findMine(userId).mainAgent()).isEqualTo("Sage");
    }

    private UpdateProfileRequest request(String mainAgent, Set<String> agents) {
        return new UpdateProfileRequest("player#test", "Gold 2",
                null, null, null, agents, mainAgent, null, null);
    }
}
