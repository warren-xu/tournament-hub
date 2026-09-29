package com.warren.warrenament.tournament;

import com.warren.warrenament.auction.AuctionRepository;
import com.warren.warrenament.auction.AuctionService;
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
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TournamentCreationTest {
    final TournamentRepository tournaments = mock(TournamentRepository.class);
    final RegistrationRepository registrations = mock(RegistrationRepository.class);
    final PlayerProfileRepository profiles = mock(PlayerProfileRepository.class);
    final TeamRepository teams = mock(TeamRepository.class);
    final TeamMemberRepository members = mock(TeamMemberRepository.class);
    final AuctionRepository auctions = mock(AuctionRepository.class);
    final AuctionService auctionService = mock(AuctionService.class);
    final TournamentService service = new TournamentService(tournaments, registrations, profiles, teams, members,
            auctions, auctionService);

    CreateTournamentRequest request(List<Long> captains) {
        return new CreateTournamentRequest(" Friday Cup ", null, 100, null, 1, null, captains);
    }

    PlayerProfile profile(long id) {
        User user = new User(); user.setId(id + 100); user.setUsername("Player " + id);
        PlayerProfile profile = new PlayerProfile(user); profile.setId(id); return profile;
    }

    Tournament tournament() {
        Tournament t = new Tournament(); t.setId(10L); t.setName("Cup"); t.setSlug("cup"); return t;
    }

    @Test void createsTeamsWithCaptainsAndOpensSignupsWithAnEmptyQueue() {
        when(profiles.findAllById(any())).thenReturn(List.of(profile(1), profile(2)));
        when(tournaments.save(any())).thenAnswer(invocation -> {
            Tournament t = invocation.getArgument(0); t.setId(10L); return t;
        });
        when(teams.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var result = service.create(request(List.of(1L, 2L)), 99L);
        assertThat(result.name()).isEqualTo("Friday Cup");
        assertThat(result.slug()).startsWith("friday-cup-");
        assertThat(result.status()).isEqualTo(TournamentStatus.REGISTRATION);
        var captains = ArgumentCaptor.forClass(TeamMember.class);
        verify(members, times(2)).save(captains.capture());
        assertThat(captains.getAllValues()).allSatisfy(member -> {
            assertThat(member.getPricePaid()).isZero();
            assertThat(member.getTeam().getCaptainUserId()).isEqualTo(member.getPlayerProfile().getUser().getId());
            assertThat(member.getTeam().getRemainingCredits()).isEqualTo(100);
        });
        verifyNoInteractions(registrations);
        verify(auctionService).createForTournament(10L);
    }

    @Test void generatesValidLinkWhenLongNameIsTruncatedAtASeparator() {
        when(profiles.findAllById(any())).thenReturn(List.of(profile(1), profile(2)));
        when(tournaments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(teams.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var request = new CreateTournamentRequest("a".repeat(89) + " next", null, 100, null, 1, null, List.of(1L, 2L));
        assertThat(service.create(request, 99L).slug()).matches("[a-z0-9]+(?:-[a-z0-9]+)*");
    }

    @Test void rejectsMissingOrDuplicateCaptains() {
        for (List<Long> captains : Arrays.asList(null, List.<Long>of(), List.of(1L), Arrays.asList(1L, null), List.of(1L, 1L))) {
            assertThatThrownBy(() -> service.create(request(captains), 99L)).isInstanceOf(BadRequestException.class);
        }
        verifyNoInteractions(tournaments, teams, members, registrations);
    }

    @Test void rejectsStaleProfilesBeforeSavingAnything() {
        when(profiles.findAllById(any())).thenReturn(List.of(profile(1)));
        assertThatThrownBy(() -> service.create(request(List.of(1L, 2L)), 99L))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("no longer available");
        verifyNoInteractions(tournaments, teams, members, registrations);
    }

    @Test void queuesChosenPlayersAsApprovedAndPutsEachInTheAuction() {
        when(tournaments.findById(10L)).thenReturn(Optional.of(tournament()));
        when(profiles.findAllById(any())).thenReturn(List.of(profile(3), profile(4), profile(5)));
        when(auctions.findByTournamentId(10L)).thenReturn(Optional.of(mock(com.warren.warrenament.auction.Auction.class)));
        when(registrations.findByTournamentIdAndPlayerProfileId(any(), any())).thenReturn(Optional.empty());
        when(registrations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var queued = service.queuePlayers(10L, List.of(3L, 4L, 5L));

        assertThat(queued).extracting(r -> r.player().id()).containsExactlyInAnyOrder(3L, 4L, 5L);
        assertThat(queued).allMatch(r -> r.status() == RegistrationStatus.APPROVED);
        verify(auctionService, times(3)).enqueue(eq(10L), any());
    }

    @Test void refusesToQueueACaptain() {
        when(tournaments.findById(10L)).thenReturn(Optional.of(tournament()));
        when(profiles.findAllById(any())).thenReturn(List.of(profile(1)));
        when(members.existsByTournamentIdAndPlayerProfileId(10L, 1L)).thenReturn(true);
        assertThatThrownBy(() -> service.queuePlayers(10L, List.of(1L)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("already on a team");
        verify(auctionService, never()).enqueue(any(), any());
    }

    @Test void refusesAnEmptySelection() {
        when(tournaments.findById(10L)).thenReturn(Optional.of(tournament()));
        assertThatThrownBy(() -> service.queuePlayers(10L, List.of())).isInstanceOf(BadRequestException.class);
    }

    @Test void removingFromTheQueueDropsTheLotAndTheRegistration() {
        when(tournaments.findById(10L)).thenReturn(Optional.of(tournament()));
        Registration registration = new Registration(tournament(), profile(3));
        when(registrations.findByTournamentIdAndPlayerProfileId(10L, 3L)).thenReturn(Optional.of(registration));
        service.removeFromQueue(10L, 3L);
        verify(auctionService).dequeue(10L, 3L);
        verify(registrations).delete(registration);
    }
}
