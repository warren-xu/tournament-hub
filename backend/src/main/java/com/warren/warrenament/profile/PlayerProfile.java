package com.warren.warrenament.profile;

import com.warren.warrenament.auth.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.ManyToOne;
import com.warren.warrenament.playercard.PlayerCard;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "player_profiles")
@Getter
@Setter
@NoArgsConstructor
public class PlayerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "riot_id")
    private String riotId;

    @Column(name = "current_rank")
    private String currentRank;

    @Column(name = "primary_role")
    private String primaryRole;

    @Column(name = "secondary_role")
    private String secondaryRole;

    @Column(name = "main_agent")
    private String mainAgent;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "player_card_id")
    private PlayerCard playerCard;

    private String bio;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "player_profile_agents",
            joinColumns = @JoinColumn(name = "player_profile_id"))
    @Column(name = "agent", nullable = false)
    private Set<String> agents = new LinkedHashSet<>();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public PlayerProfile(User user) {
        this.user = user;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }
}
