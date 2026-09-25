package com.warren.warrenament.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The authenticated principal for both Discord login and the dev-profile login shortcut.
 * Carries our own {@code users.id} so services never have to re-resolve the Discord id.
 */
public record AppUser(
        Long userId,
        String discordId,
        String username,
        String avatarUrl,
        Role role,
        Map<String, Object> attributes
) implements OAuth2User {

    public static AppUser from(User user, Map<String, Object> attributes) {
        return new AppUser(
                user.getId(),
                user.getDiscordId(),
                user.getUsername(),
                user.getAvatarUrl(),
                user.getRole(),
                attributes == null ? Map.of() : attributes);
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    /** Must be the Discord id: it is the {@code user-name-attribute} of the registration. */
    @Override
    public String getName() {
        return discordId;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
