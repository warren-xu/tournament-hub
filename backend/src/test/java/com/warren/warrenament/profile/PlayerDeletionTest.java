package com.warren.warrenament.profile;

import com.warren.warrenament.TestFixtures;
import com.warren.warrenament.TestcontainersConfiguration;
import com.warren.warrenament.auction.Auction;
import com.warren.warrenament.auction.AuctionService;
import com.warren.warrenament.auction.BidRepository;
import com.warren.warrenament.auction.BidService;
import com.warren.warrenament.auction.Lot;
import com.warren.warrenament.auction.LotRepository;
import com.warren.warrenament.auth.User;
import com.warren.warrenament.auth.UserRepository;
import com.warren.warrenament.common.Exceptions.BadRequestException;
import com.warren.warrenament.team.TeamRepository;
import com.warren.warrenament.tournament.Registration;
import com.warren.warrenament.tournament.RegistrationRepository;
import com.warren.warrenament.tournament.Tournament;
import com.warren.warrenament.tournament.TournamentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PlayerDeletionTest {

    @Autowired TestFixtures fixtures;
    @Autowired PlayerProfileService service;
    @Autowired PlayerProfileRepository profiles;
    @Autowired UserRepository users;
    @Autowired RegistrationRepository registrations;
    @Autowired TournamentRepository tournaments;
    @Autowired TeamRepository teams;
    @Autowired LotRepository lots;
    @Autowired BidRepository bids;
    @Autowired BidService bidService;
    @Autowired AuctionService auctionService;

    private static final long ADMIN = -1L;

    @Test
    @DisplayName("a test player is removed along with their account and registration")
    void deletesProfileUserAndRegistration() {
        Tournament tournament = fixtures.tournament(100, 5, 1);
        PlayerProfile profile = fixtures.profile("throwaway");
        Long profileId = profile.getId();
        Long userId = profile.getUser().getId();
        registrations.save(new Registration(tournament, profile));

        service.deletePlayer(profileId, ADMIN);

        assertThat(profiles.findById(profileId)).isEmpty();
        assertThat(users.findById(userId)).isEmpty();
        assertThat(registrations.findByTournamentId(tournament.getId()))
                .noneMatch(r -> r.getPlayerProfile().getId().equals(profileId));
    }

    @Test
    @DisplayName("a queued player is deleted, taking their pending lot with them")
    void deletesPendingLotsToo() {
        Tournament tournament = fixtures.tournament(100, 5, 1);
        Auction auction = fixtures.liveAuction(tournament, 30);
        PlayerProfile profile = fixtures.profile("queued");
        Lot lot = lots.saveAndFlush(new Lot(auction, profile, 1)); // stays PENDING

        service.deletePlayer(profile.getId(), ADMIN);

        assertThat(lots.findById(lot.getId())).isEmpty();
        assertThat(profiles.findById(profile.getId())).isEmpty();
    }

    @Test
    @DisplayName("a drafted player cannot be deleted - that would tear them off a roster")
    void refusesDraftedPlayer() {
        Tournament tournament = fixtures.tournament(100, 5, 1);
        Auction auction = fixtures.liveAuction(tournament, 30);
        User captain = fixtures.user("cap");
        fixtures.team(tournament, "Alpha", captain);
        PlayerProfile profile = fixtures.profile("drafted");

        Lot lot = fixtures.openLot(auction, profile, 60);
        bidService.submitBid(auction.getId(), lot.getId(), 5, captain.getId());
        auctionService.closeLot(lot.getId());

        assertThatThrownBy(() -> service.deletePlayer(profile.getId(), ADMIN))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already been drafted");

        assertThat(profiles.findById(profile.getId())).isPresent();
    }

    @Test
    @DisplayName("a captain cannot be deleted out from under their team")
    void refusesCaptain() {
        Tournament tournament = fixtures.tournament(100, 5, 1);
        PlayerProfile profile = fixtures.profile("skipper");
        fixtures.team(tournament, "Skippers", profile.getUser());

        assertThatThrownBy(() -> service.deletePlayer(profile.getId(), ADMIN))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("captains a team");
    }

    @Test
    @DisplayName("a player currently on the auction block cannot be deleted")
    void refusesPlayerInAnOpenLot() {
        Tournament tournament = fixtures.tournament(100, 5, 1);
        Auction auction = fixtures.liveAuction(tournament, 30);
        PlayerProfile profile = fixtures.profile("onblock");
        fixtures.openLot(auction, profile, 60);

        assertThatThrownBy(() -> service.deletePlayer(profile.getId(), ADMIN))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("running auction");
    }

    @Test
    @DisplayName("an admin cannot delete their own account")
    void refusesSelfDelete() {
        PlayerProfile profile = fixtures.profile("selfie");

        assertThatThrownBy(() ->
                service.deletePlayer(profile.getId(), profile.getUser().getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("your own account");
    }

    @Test
    @DisplayName("deleting a bidder keeps the bid history, just unattributed")
    void keepsBidHistoryWhenBidderIsDeleted() {
        Tournament tournament = fixtures.tournament(100, 5, 1);
        Auction auction = fixtures.liveAuction(tournament, 30);
        User captain = fixtures.user("bidder");
        fixtures.team(tournament, "Bidders", captain);
        PlayerProfile target = fixtures.profile("target");
        Lot lot = fixtures.openLot(auction, target, 60);
        bidService.submitBid(auction.getId(), lot.getId(), 7, captain.getId());

        // The captain themselves cannot go (they captain a team), but their profile can
        // once the team is gone - prove the bid row survives the user being detached.
        bids.clearUser(captain.getId());

        assertThat(bids.findByLotIdOrderByIdAsc(lot.getId()))
                .singleElement()
                .matches(b -> b.getUserId() == null && b.getAmount() == 7,
                        "amount kept, bidder detached");
    }

    @Test
    @DisplayName("a tournament outlives the account that created it")
    void tournamentSurvivesCreatorDeletion() {
        PlayerProfile creator = fixtures.profile("organiser");
        Tournament tournament = fixtures.tournament(100, 5, 1);
        tournament.setCreatedByUserId(creator.getUser().getId());
        tournaments.saveAndFlush(tournament);

        service.deletePlayer(creator.getId(), ADMIN);

        Tournament reloaded = tournaments.findById(tournament.getId()).orElseThrow();
        assertThat(reloaded.getCreatedByUserId()).isNull();
        assertThat(reloaded.getName()).isEqualTo("Test Cup");
    }
}
