package com.warren.warrenament.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "discord_id", nullable = false, unique = true)
    private String discordId;

    @Column(nullable = false)
    private String username;

    @Column(name = "avatar_url")
    private String avatarUrl;

    /** Discord profile banner; only Nitro users have one. */
    @Column(name = "banner_url")
    private String bannerUrl;

    /** Discord's profile accent colour as 0xRRGGBB, the fallback when there is no banner. */
    @Column(name = "accent_color")
    private Integer accentColor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.PLAYER;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    public User(String discordId, String username, String avatarUrl, Role role) {
        this.discordId = discordId;
        this.username = username;
        this.avatarUrl = avatarUrl;
        this.role = role;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
