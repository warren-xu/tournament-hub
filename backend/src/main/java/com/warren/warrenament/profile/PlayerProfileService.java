package com.warren.warrenament.profile;

import com.warren.warrenament.auction.Lot;
import com.warren.warrenament.auction.LotRepository;
import com.warren.warrenament.auction.LotStatus;
import com.warren.warrenament.auction.BidRepository;
import com.warren.warrenament.auth.User;
import com.warren.warrenament.auth.UserRepository;
import com.warren.warrenament.common.Exceptions.BadRequestException;
import com.warren.warrenament.common.Exceptions.NotFoundException;
import com.warren.warrenament.team.TeamMemberRepository;
import com.warren.warrenament.team.TeamRepository;
import com.warren.warrenament.tournament.TournamentRepository;
import com.warren.warrenament.profile.PlayerProfileDtos.ProfileView;
import com.warren.warrenament.profile.PlayerProfileDtos.UpdateProfileRequest;
import org.springframework.stereotype.Service;
import com.warren.warrenament.playercard.PlayerCardRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class PlayerProfileService {

    private final PlayerCardRepository playerCards;
    private final PlayerProfileRepository profiles;
    private final UserRepository users;
    private final TeamMemberRepository teamMembers;
    private final TeamRepository teams;
    private final LotRepository lots;
    private final BidRepository bids;
    private final TournamentRepository tournaments;

    public PlayerProfileService(PlayerProfileRepository profiles,
                                UserRepository users,
                                TeamMemberRepository teamMembers,
                                TeamRepository teams,
                                LotRepository lots,
                                BidRepository bids,
                                TournamentRepository tournaments,
                                PlayerCardRepository playerCards) {
        this.playerCards = playerCards;
        this.profiles = profiles;
        this.users = users;
        this.teamMembers = teamMembers;
        this.teams = teams;
        this.lots = lots;
        this.bids = bids;
        this.tournaments = tournaments;
    }

    /** Called on first login so every user has a profile row to edit. */
    @Transactional
    public PlayerProfile ensureProfile(User user) {
        return profiles.findByUserId(user.getId())
                .orElseGet(() -> profiles.save(new PlayerProfile(user)));
    }

    @Transactional(readOnly = true)
    public List<ProfileView> findAll() {
        return profiles.findAll().stream().map(ProfileView::of).toList();
    }

    @Transactional(readOnly = true)
    public ProfileView findById(Long id) {
        return profiles.findById(id).map(ProfileView::of)
                .orElseThrow(() -> NotFoundException.of("Profile", id));
    }

    @Transactional(readOnly = true)
    public ProfileView findMine(Long userId) {
        return profiles.findByUserId(userId).map(ProfileView::of)
                .orElseThrow(() -> new NotFoundException("No profile for the current user"));
    }

    @Transactional
    public ProfileView updateMine(Long userId, UpdateProfileRequest request) {
        PlayerProfile profile = profiles.findByUserId(userId)
                .orElseGet(() -> {
                    User user = users.findById(userId)
                            .orElseThrow(() -> NotFoundException.of("User", userId));
                    return profiles.save(new PlayerProfile(user));
                });

        String mainAgent = request.mainAgent() == null || request.mainAgent().isBlank()
                ? null : request.mainAgent().trim();
        if (mainAgent != null && (request.agents() == null || !request.agents().contains(mainAgent))) {
            throw new BadRequestException("Your main agent must be in your agent pool.");
        }
        var playerCard = request.playerCardId() == null ? null
                : playerCards.findById(request.playerCardId()).orElseThrow(() ->
                    new BadRequestException("Choose a player card from the synced catalog."));
        profile.setPlayerCard(playerCard);
        profile.setMainAgent(mainAgent);
        profile.setRiotId(request.riotId());
        profile.setCurrentRank(request.currentRank());
        profile.setPeakRank(request.peakRank());
        profile.setPrimaryRole(request.primaryRole());
        profile.setSecondaryRole(request.secondaryRole());
        profile.setBio(request.bio());
        profile.setAgents(request.agents() == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(request.agents()));
        profile.setUpdatedAt(Instant.now());

        return ProfileView.of(profiles.save(profile));
    }

    /**
     * Permanently removes a player: their profile, agent pool, registrations and the
     * user account behind them. Intended for clearing out test entries before an event.
     * <p>
     * Refuses in the three cases where a delete would corrupt something rather than just
     * tidy up — a drafted player, a team captain, or a player currently in an auction.
     * Bid history and tournaments survive by releasing their reference to the user
     * rather than cascading the delete into them.
     */
    @Transactional
    public void deletePlayer(Long profileId, Long callerUserId) {
        PlayerProfile profile = profiles.findById(profileId)
                .orElseThrow(() -> NotFoundException.of("Profile", profileId));
        User user = profile.getUser();
        String name = user.getUsername();

        if (user.getId().equals(callerUserId)) {
            throw new BadRequestException("You cannot delete your own account.");
        }
        if (teamMembers.existsByPlayerProfileId(profileId)) {
            throw new BadRequestException(
                    name + " has already been drafted onto a roster. Undo the sale first.");
        }
        if (teams.existsByCaptainUserId(user.getId())) {
            throw new BadRequestException(
                    name + " captains a team. Reassign the captain or delete the team first.");
        }

        List<Lot> playerLots = lots.findByPlayerProfileId(profileId);
        if (playerLots.stream().anyMatch(lot -> lot.getStatus() == LotStatus.OPEN
                || lot.getStatus() == LotStatus.SOLD)) {
            throw new BadRequestException(
                    name + " is in a running auction. Close or undo that lot first.");
        }

        // Queued lots reference the profile and have no bids yet, so they go with it.
        lots.deleteAll(playerLots);
        lots.flush();

        bids.clearUser(user.getId());
        tournaments.clearCreator(user.getId());

        // Registrations and the agent pool are removed by the schema's cascades.
        profiles.delete(profile);
        profiles.flush();
        users.delete(user);
    }
}