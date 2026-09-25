package com.warren.warrenament.tournament;

import com.warren.warrenament.TestFixtures;
import com.warren.warrenament.TestcontainersConfiguration;
import com.warren.warrenament.auction.Auction;
import com.warren.warrenament.auction.AuctionRepository;
import com.warren.warrenament.auction.AuctionService;
import com.warren.warrenament.auction.AuctionStatus;
import com.warren.warrenament.auction.BidRepository;
import com.warren.warrenament.auction.BidService;
import com.warren.warrenament.auction.Lot;
import com.warren.warrenament.auction.LotRepository;
import com.warren.warrenament.auth.User;
import com.warren.warrenament.common.Exceptions.BadRequestException;
import com.warren.warrenament.common.Exceptions.NotFoundException;
import com.warren.warrenament.profile.PlayerProfile;
import com.warren.warrenament.profile.PlayerProfileRepository;
import com.warren.warrenament.team.Team;
import com.warren.warrenament.team.TeamRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TournamentDeletionTest {

    @Autowired TestFixtures fixtures;
    @Autowired TournamentService service;
    @Autowired TournamentRepository tournaments;
    @Autowired RegistrationRepository registrations;
    @Autowired TeamRepository teams;
    @Autowired AuctionRepository auctions;
    @Autowired LotRepository lots;
    @Autowired BidRepository bids;
    @Autowired BidService bidService;
    @Autowired AuctionService auctionService;
    @Autowired PlayerProfileRepository profiles;

    @Test
    @DisplayName("a drafted tournament is removed with its teams, registrations, auction and bids")
    void deletesEverythingUnderTheTournament() {
        Tournament tournament = fixtures.tournament(100, 5, 1);
        Auction auction = fixtures.liveAuction(tournament, 30);
        User captain = fixtures.user("cap");
        Team team = fixtures.team(tournament, "Alpha", captain);
        PlayerProfile player = fixtures.profile("drafted");
        registrations.save(new Registration(tournament, player));

        Lot lot = fixtures.openLot(auction, player, 60);
        bidService.submitBid(auction.getId(), lot.getId(), 5, captain.getId());
        auctionService.closeLot(lot.getId());
        auction = auctions.findById(auction.getId()).orElseThrow();
        auction.setStatus(AuctionStatus.COMPLETE);
        auctions.saveAndFlush(auction);

        service.delete(tournament.getId());

        assertThat(tournaments.findById(tournament.getId())).isEmpty();
        assertThat(registrations.findByTournamentId(tournament.getId())).isEmpty();
        assertThat(teams.findById(team.getId())).isEmpty();
        assertThat(auctions.findById(auction.getId())).isEmpty();
        assertThat(lots.findById(lot.getId())).isEmpty();
        assertThat(bids.findByLotIdOrderByIdAsc(lot.getId())).isEmpty();
        // Players are not owned by a tournament.
        assertThat(profiles.findById(player.getId())).isPresent();
    }

    @Test
    @DisplayName("a tournament cannot be deleted while its auction is live")
    void refusesLiveAuction() {
        Tournament tournament = fixtures.tournament(100, 5, 1);
        fixtures.liveAuction(tournament, 30);

        assertThatThrownBy(() -> service.delete(tournament.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("is live");

        assertThat(tournaments.findById(tournament.getId())).isPresent();
    }

    @Test
    @DisplayName("deleting a missing tournament is a 404")
    void missingTournament() {
        assertThatThrownBy(() -> service.delete(-42L))
                .isInstanceOf(NotFoundException.class);
    }
}
