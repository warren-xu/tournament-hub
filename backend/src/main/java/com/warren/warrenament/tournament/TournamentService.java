package com.warren.warrenament.tournament;

import com.warren.warrenament.auction.AuctionRepository;
import com.warren.warrenament.auction.AuctionService;
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

import java.time.Instant;
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
    private final AuctionService auctionService;

    public TournamentService(TournamentRepository tournaments,
                             RegistrationRepository registrations,
                             PlayerProfileRepository profiles,
                             TeamRepository teams, TeamMemberRepository members,
                             AuctionRepository auctions, AuctionService auctionService) {
        this.tournaments = tournaments;
        this.registrations = registrations;
        this.profiles = profiles;
        this.teams = teams;
        this.members = members;
        this.auctions = auctions;
        this.auctionService = auctionService;
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

    /**
     * Creates the tournament with a team per captain and opens sign-ups straight away.
     * Nobody else is queued yet: players join by signing up, or an admin shuffles them in
     * from the pool. Team size is worked out when the draft starts.
     */
    @Transactional
    public TournamentView create(CreateTournamentRequest request, Long createdByUserId) {
        List<Long> captainIds = request.captainProfileIds();
        if (captainIds == null || captainIds.size() < 2 || captainIds.stream().anyMatch(Objects::isNull)) {
            throw new BadRequestException("Choose a captain for every team (at least two teams).");
        }
        if (new HashSet<>(captainIds).size() != captainIds.size()) {
            throw new BadRequestException("Each captain can only lead one team.");
        }
        var captains = profiles.findAllById(captainIds).stream()
                .collect(java.util.stream.Collectors.toMap(PlayerProfile::getId, p -> p));
        if (captains.size() != captainIds.size()) {
            throw new BadRequestException("A selected captain is no longer available. Refresh the player list and try again.");
        }
        Tournament tournament = newTournament(request, createdByUserId);
        tournament.setStatus(TournamentStatus.REGISTRATION);
        tournament = tournaments.save(tournament);
        for (Long id : captainIds) {
            PlayerProfile captain = captains.get(id);
            // Named after the captain to start with; captains and admins can rename it.
            String teamName = captain.getUser().getUsername();
            Team team = teams.save(new Team(tournament, teamName.substring(0, Math.min(teamName.length(), 128)),
                    null, captain.getUser().getId()));
            members.save(new TeamMember(team, captain, 0));
        }
        auctionService.createForTournament(tournament.getId());
        return TournamentView.of(tournament);
    }

    /** Name, link, budget and date, checked and applied to a new tournament. */
    private Tournament newTournament(CreateTournamentRequest request, Long createdByUserId) {
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
        tournament.setMinBid(Objects.requireNonNullElse(request.minBid(), 1));
        tournament.setStartsAt(request.startsAt());
        if (tournament.getMinBid() > tournament.getCreditBudget()) {
            throw new BadRequestException("The minimum bid cannot exceed the team's starting credits.");
        }
        return tournament;
    }

    @Transactional
    public TournamentView updateStatus(Long id, TournamentStatus status) {
        Tournament tournament = require(id);
        tournament.setStatus(status);
        return TournamentView.of(tournaments.save(tournament));
    }

    @Transactional
    public TournamentView updateSchedule(Long id, Instant startsAt) {
        Tournament tournament = require(id);
        tournament.setStartsAt(startsAt);
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

    /**
     * A player adds themselves to the auction queue. Everyone is already in the pool;
     * signing up is what puts them up for auction.
     */
    @Transactional
    public RegistrationView register(Long tournamentId, Long userId) {
        Tournament tournament = require(tournamentId);
        if (tournament.getStatus() != TournamentStatus.REGISTRATION) {
            throw new BadRequestException("Sign-ups are not open for this tournament.");
        }
        PlayerProfile profile = profiles.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Create your player profile first."));
        if (profile.getCurrentRank() == null || profile.getCurrentRank().isBlank()) {
            throw new BadRequestException("Add your current rank to your profile first. Captains rank players by it.");
        }
        return RegistrationView.of(queue(tournament, profile));
    }

    /** A player takes themselves back out of the queue. Only before the draft starts. */
    @Transactional
    public void withdraw(Long tournamentId, Long userId) {
        PlayerProfile profile = profiles.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("No profile for the current user"));
        if (!registrations.existsByTournamentIdAndPlayerProfileId(tournamentId, profile.getId())) {
            throw new NotFoundException("You aren't in the queue for this tournament.");
        }
        unqueue(tournamentId, profile.getId());
    }

    /**
     * An admin puts players into the queue by hand. Like sign-ups, they take their place by
     * rank. Anyone already queued is left as they are. Works whether or not sign-ups are
     * open, until the draft starts.
     */
    @Transactional
    public List<RegistrationView> queuePlayers(Long tournamentId, List<Long> profileIds) {
        Tournament tournament = require(tournamentId);
        if (profileIds == null || profileIds.isEmpty()) {
            throw new BadRequestException("Choose at least one player to add to the queue.");
        }
        List<PlayerProfile> chosen = new ArrayList<>(profiles.findAllById(new HashSet<>(profileIds)));
        if (chosen.size() != new HashSet<>(profileIds).size()) {
            throw new BadRequestException("A selected player is no longer available. Refresh and try again.");
        }
        return chosen.stream().map(profile -> RegistrationView.of(queue(tournament, profile))).toList();
    }

    /** An admin takes a player back out of the queue. They stay in the pool and can sign up again. */
    @Transactional
    public void removeFromQueue(Long tournamentId, Long profileId) {
        require(tournamentId);
        unqueue(tournamentId, profileId);
    }

    /** The registration marks a player as queued; the lot is their place in the auction. */
    private Registration queue(Tournament tournament, PlayerProfile profile) {
        Long tournamentId = tournament.getId();
        auctionService.requireNotStarted(tournamentId);
        if (members.existsByTournamentIdAndPlayerProfileId(tournamentId, profile.getId())) {
            throw new BadRequestException(profile.getUser().getUsername() + " is already on a team in this tournament.");
        }
        // Tournaments from before auctions were created up front get theirs now.
        if (auctions.findByTournamentId(tournamentId).isEmpty()) {
            auctionService.createForTournament(tournamentId);
        }
        Registration registration = registrations.findByTournamentIdAndPlayerProfileId(tournamentId, profile.getId())
                .orElseGet(() -> new Registration(tournament, profile));
        registration.setStatus(RegistrationStatus.APPROVED);
        registration = registrations.save(registration);
        auctionService.enqueue(tournamentId, profile);
        return registration;
    }

    private void unqueue(Long tournamentId, Long profileId) {
        auctionService.dequeue(tournamentId, profileId);
        registrations.findByTournamentIdAndPlayerProfileId(tournamentId, profileId)
                .ifPresent(registrations::delete);
    }

    private Tournament require(Long id) {
        return tournaments.findById(id)
                .orElseThrow(() -> NotFoundException.of("Tournament", id));
    }
}
