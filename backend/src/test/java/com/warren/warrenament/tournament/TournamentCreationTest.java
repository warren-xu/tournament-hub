package com.warren.warrenament.tournament;

import com.warren.warrenament.auth.User;
import com.warren.warrenament.common.Exceptions.BadRequestException;
import com.warren.warrenament.profile.PlayerProfile;
import com.warren.warrenament.profile.PlayerProfileRepository;
import com.warren.warrenament.team.*;
import com.warren.warrenament.tournament.TournamentDtos.CreateTournamentRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.Arrays;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TournamentCreationTest {
    final TournamentRepository tournaments = mock(TournamentRepository.class);
    final RegistrationRepository registrations = mock(RegistrationRepository.class);
    final PlayerProfileRepository profiles = mock(PlayerProfileRepository.class);
    final TeamRepository teams = mock(TeamRepository.class);
    final TeamMemberRepository members = mock(TeamMemberRepository.class);
    final TournamentService service = new TournamentService(tournaments, registrations, profiles, teams, members,
            mock(com.warren.warrenament.auction.AuctionRepository.class));

    CreateTournamentRequest request(List<Long> captains, List<Long> players) {
        return new CreateTournamentRequest(" Friday Cup ", null, 100, null, 1, captains, players);
    }

    PlayerProfile profile(long id) {
        User user = new User(); user.setId(id + 100); user.setUsername("Player " + id);
        PlayerProfile profile = new PlayerProfile(user); profile.setId(id); return profile;
    }

    @Test void createsTeamsWithCaptainsAndOnlyRegistersDraftPlayers() {
        var selected = List.of(profile(1), profile(2), profile(3), profile(4), profile(5), profile(6));
        when(profiles.findAllById(any())).thenReturn(selected);
        when(tournaments.save(any())).thenAnswer(invocation -> {
            Tournament t = invocation.getArgument(0); t.setId(10L); return t;
        });
        when(teams.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var result = service.create(request(List.of(1L, 2L), List.of(3L, 4L, 5L, 6L)), 99L);
        assertThat(result.rosterSize()).isEqualTo(3);
        assertThat(result.name()).isEqualTo("Friday Cup");
        assertThat(result.slug()).startsWith("friday-cup-");
        assertThat(result.status()).isEqualTo(TournamentStatus.DRAFT);
        var captains = ArgumentCaptor.forClass(TeamMember.class);
        verify(members, times(2)).save(captains.capture());
        assertThat(captains.getAllValues()).allSatisfy(member -> {
            assertThat(member.getPricePaid()).isZero();
            assertThat(member.getTeam().getCaptainUserId()).isEqualTo(member.getPlayerProfile().getUser().getId());
            assertThat(member.getTeam().getRemainingCredits()).isEqualTo(100);
        });
        var pool = ArgumentCaptor.forClass(Registration.class);
        verify(registrations, times(4)).save(pool.capture());
        assertThat(pool.getAllValues()).extracting(r -> r.getPlayerProfile().getId()).containsExactly(3L, 4L, 5L, 6L);
        assertThat(pool.getAllValues()).allMatch(r -> r.getStatus() == RegistrationStatus.APPROVED);
    }

    @Test void generatesValidLinkWhenLongNameIsTruncatedAtASeparator() {
        when(profiles.findAllById(any())).thenReturn(List.of(profile(1), profile(2), profile(3), profile(4)));
        when(tournaments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(teams.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var request = new CreateTournamentRequest("a".repeat(89) + " next", null, 100, null, 1,
                List.of(1L, 2L), List.of(3L, 4L));
        assertThat(service.create(request, 99L).slug()).matches("[a-z0-9]+(?:-[a-z0-9]+)*");
    }

    @Test void rejectsMissingCaptains() {
        for (List<Long> captains : Arrays.asList(null, List.<Long>of(), List.of(1L), Arrays.asList(1L, null))) {
            assertThatThrownBy(() -> service.create(request(captains, List.of(3L, 4L)), 99L)).isInstanceOf(BadRequestException.class);
        }
        verifyNoInteractions(tournaments, teams, members, registrations);
    }

    @Test void rejectsDuplicatesAndCaptainsInDraftPool() {
        for (var invalid : List.of(request(List.of(1L, 1L), List.of(3L, 4L)),
                request(List.of(1L, 2L), List.of(3L, 3L)), request(List.of(1L, 2L), List.of(1L, 3L)))) {
            assertThatThrownBy(() -> service.create(invalid, 99L)).isInstanceOf(BadRequestException.class);
        }
        verifyNoInteractions(tournaments, teams, members, registrations);
    }

    @Test void rejectsUnevenOrEmptyPool() {
        for (var players : List.of(List.<Long>of(), List.of(3L, 4L, 5L))) {
            assertThatThrownBy(() -> service.create(request(List.of(1L, 2L), players), 99L)).isInstanceOf(BadRequestException.class);
        }
        verifyNoInteractions(tournaments, teams, members, registrations);
    }

    @Test void rejectsStaleProfilesBeforeSavingAnything() {
        when(profiles.findAllById(any())).thenReturn(List.of(profile(1), profile(2), profile(3)));
        assertThatThrownBy(() -> service.create(request(List.of(1L, 2L), List.of(3L, 4L)), 99L))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("no longer available");
        verifyNoInteractions(tournaments, teams, members, registrations);
    }

    @Test void rejectsInconsistentTeamSize() {
        var request = new CreateTournamentRequest("Cup", null, 100, 5, 1, List.of(1L, 2L), List.of(3L, 4L));
        assertThatThrownBy(() -> service.create(request, 99L)).isInstanceOf(BadRequestException.class).hasMessageContaining("Team size");
        verifyNoInteractions(tournaments, teams, members, registrations);
    }
}
