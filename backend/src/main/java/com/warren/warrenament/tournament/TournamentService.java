package com.warren.warrenament.tournament;

import com.warren.warrenament.auction.AuctionRepository;
import com.warren.warrenament.auction.AuctionStatus;
import com.warren.warrenament.common.Exceptions.BadRequestException;
import com.warren.warrenament.common.Exceptions.NotFoundException;
import com.warren.warrenament.profile.PlayerProfile;
import com.warren.warrenament.profile.PlayerProfileRepository;
import com.warren.warrenament.tournament.TournamentDtos.CreateTournamentRequest;
import com.warren.warrenament.tournament.TournamentDtos.RegistrationView;
import com.warren.warrenament.tournament.TournamentDtos.TournamentView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import com.warren.warrenament.team.Team;
import com.warren.warrenament.team.TeamRepository;
import com.warren.warrenament.team.TeamMember;
import com.warren.warrenament.team.TeamMemberRepository;

@Service
public class TournamentService {

    private final TournamentRepository tournaments;
    private final RegistrationRepository registrations;
    private final PlayerProfileRepository profiles;

    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final AuctionRepository auctions;

    public TournamentService(TournamentRepository tournaments,
                             RegistrationRepository registrations,
                             PlayerProfileRepository profiles,
                             TeamRepository teams, TeamMemberRepository members,
                             AuctionRepository auctions) {
        this.tournaments = tournaments;
        this.registrations = registrations;
        this.profiles = profiles;
        this.teams = teams;
        this.members = members;
        this.auctions = auctions;
    }

    @Transactional(readOnly = true)
    public List<TournamentView> findAll() {
        return tournaments.findAll().stream().map(TournamentView::of).toList();
    }

    @Transactional(readOnly = true)
    public TournamentView findById(Long id) {
        return TournamentView.of(require(id));
    }

    @Transactional(readOnly = true)
    public TournamentView findBySlug(String slug) {
        return tournaments.findBySlug(slug).map(TournamentView::of)
                .orElseThrow(() -> new NotFoundException("Tournament " + slug + " not found"));
    }

    @Transactional
    public TournamentView create(CreateTournamentRequest request, Long createdByUserId) {
        List<Long> captainIds = request.captainProfileIds();
        List<Long> playerIds = request.playerProfileIds();
        if (captainIds == null || captainIds.size() < 2 || captainIds.stream().anyMatch(Objects::isNull)) {
            throw new BadRequestException("Choose a captain for every team (at least two teams).");
        }
        if (playerIds == null || playerIds.isEmpty() || playerIds.stream().anyMatch(Objects::isNull)) {
            throw new BadRequestException("Choose the players who will take part in the draft.");
        }
        if (new HashSet<>(captainIds).size() != captainIds.size()
                || new HashSet<>(playerIds).size() != playerIds.size()) {
            throw new BadRequestException("Each captain and player can only be selected once.");
        }
        if (captainIds.stream().anyMatch(playerIds::contains)) {
            throw new BadRequestException("Captains already have a team. Remove them from the draft players.");
        }
        if (playerIds.size() % captainIds.size() != 0) {
            throw new BadRequestException("The draft players must split evenly between the captains.");
        }
        int rosterSize = 1 + playerIds.size() / captainIds.size();
        if (rosterSize > 10 || (request.rosterSize() != null && request.rosterSize() != rosterSize)) {
            throw new BadRequestException("Team size must match the selected players, with at most 10 people including the captain.");
        }
        List<Long> allIds = new ArrayList<>(captainIds);
        allIds.addAll(playerIds);
        var selected = profiles.findAllById(allIds).stream()
                .collect(java.util.stream.Collectors.toMap(PlayerProfile::getId, p -> p));
        if (selected.size() != allIds.size()) {
            throw new BadRequestException("A selected player is no longer available. Refresh the player list and try again.");
        }
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isEmpty() || name.length() > 128) {
            throw new BadRequestException("Enter a tournament name of up to 128 characters.");
        }
        String slug = request.slug();
        if (slug == null || slug.isBlank()) {
            String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
            if (base.isEmpty()) base = "tournament";
            slug = base.substring(0, Math.min(base.length(), 90)).replaceAll("-+$", "") + "-" + UUID.randomUUID().toString().substring(0, 8);
        }
        if (!slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*") || slug.length() > 128) {
            throw new BadRequestException("The tournament link must contain only lowercase letters, numbers and hyphens.");
        }
        if (tournaments.existsBySlug(slug)) {
            throw new BadRequestException("That tournament link is already in use. Choose a different one.");
        }
        Tournament tournament = new Tournament();
        tournament.setName(name);
        tournament.setSlug(slug);
        tournament.setCreatedByUserId(createdByUserId);
        tournament.setCreditBudget(Objects.requireNonNullElse(request.creditBudget(), 100));
        tournament.setRosterSize(rosterSize);
        tournament.setMinBid(Objects.requireNonNullElse(request.minBid(), 1));
        if (tournament.getMinBid() > tournament.getCreditBudget()) {
            throw new BadRequestException("The minimum bid cannot exceed the team's starting credits.");
        }
        tournament = tournaments.save(tournament);
        int teamNumber = 0;
        for (Long id : captainIds) {
            PlayerProfile captain = selected.get(id);
            String teamName = "Team " + (++teamNumber) + " · " + captain.getUser().getUsername();
            Team team = teams.save(new Team(tournament, teamName.substring(0, Math.min(teamName.length(), 128)),
                    null, captain.getUser().getId()));
            members.save(new TeamMember(team, captain, 0));
        }
        for (Long id : playerIds) {
            Registration registration = new Registration(tournament, selected.get(id));
            registration.setStatus(RegistrationStatus.APPROVED);
            registrations.save(registration);
        }
        return TournamentView.of(tournament);
    }

    @Transactional
    public TournamentView updateStatus(Long id, TournamentStatus status) {
        Tournament tournament = require(id);
        tournament.setStatus(status);
        return TournamentView.of(tournaments.save(tournament));
    }

    /**
     * Permanently removes a tournament. Registrations, teams, rosters, the auction, its
     * lots and bid history all go with it via the schema's cascades.
     * <p>
     * Refuses while the auction is live: the lot sweeper would be closing lots out from
     * under the delete. Pause the auction first.
     */
    @Transactional
    public void delete(Long id) {
        Tournament tournament = require(id);
        // Checked with a query rather than loading the Auction: a managed Auction pointing at
        // the removed Tournament would fail the flush.
        if (auctions.existsByTournamentIdAndStatus(id, AuctionStatus.LIVE)) {
            throw new BadRequestException(
                    "The auction for " + tournament.getName() + " is live. Pause it before deleting the tournament.");
        }
        // Lots and bids reference teams without cascading, so the auction (and with it
        // every lot and bid) must be gone before the tournament cascade reaches the teams.
        auctions.deleteByTournamentIdInBulk(id);
        tournaments.delete(tournament);
    }

    @Transactional(readOnly = true)
    public List<RegistrationView> registrations(Long tournamentId) {
        return registrations.findByTournamentId(tournamentId).stream()
                .map(RegistrationView::of).toList();
    }

    /** A player signs themselves up for the draft pool. */
    @Transactional
    public RegistrationView register(Long tournamentId, Long userId) {
        Tournament tournament = require(tournamentId);
        if (tournament.getStatus() != TournamentStatus.REGISTRATION) {
            throw new BadRequestException("Registration is not open for this tournament");
        }
        PlayerProfile profile = profiles.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Create your player profile first"));

        return registrations.findByTournamentIdAndPlayerProfileId(tournamentId, profile.getId())
                .map(RegistrationView::of)
                .orElseGet(() -> RegistrationView.of(
                        registrations.save(new Registration(tournament, profile))));
    }

    @Transactional
    public RegistrationView setRegistrationStatus(Long registrationId, RegistrationStatus status) {
        Registration registration = registrations.findById(registrationId)
                .orElseThrow(() -> NotFoundException.of("Registration", registrationId));
        registration.setStatus(status);
        return RegistrationView.of(registrations.save(registration));
    }

    private Tournament require(Long id) {
        return tournaments.findById(id)
                .orElseThrow(() -> NotFoundException.of("Tournament", id));
    }
}
