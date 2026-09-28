package com.warren.warrenament.auth;

import com.warren.warrenament.profile.PlayerProfileService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Upserts a {@link User} from the Discord profile on every login, and guarantees the user
 * has an (initially empty) player profile to fill in.
 */
@Service
public class DiscordOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final UserRepository users;
    private final PlayerProfileService profiles;
    private final AuthProperties authProperties;

    public DiscordOAuth2UserService(UserRepository users,
                                    PlayerProfileService profiles,
                                    AuthProperties authProperties) {
        this.users = users;
        this.profiles = profiles;
        this.authProperties = authProperties;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User oauthUser = delegate.loadUser(request);
        Map<String, Object> attributes = oauthUser.getAttributes();

        String discordId = String.valueOf(attributes.get("id"));
        String username = firstNonBlank(
                (String) attributes.get("global_name"),
                (String) attributes.get("username"),
                discordId);
        String avatarUrl = avatarUrl(discordId, (String) attributes.get("avatar"));

        User user = users.findByDiscordId(discordId).orElseGet(User::new);
        user.setDiscordId(discordId);
        user.setUsername(username);
        user.setAvatarUrl(avatarUrl);
        user.setBannerUrl(bannerUrl(discordId, (String) attributes.get("banner")));
        user.setAccentColor(attributes.get("accent_color") instanceof Number n ? n.intValue() : null);
        // Admins are configured by Discord id, so promotion survives a database reset.
        if (authProperties.adminDiscordIds().contains(discordId)) {
            user.setRole(Role.ADMIN);
        } else if (user.getRole() == null) {
            user.setRole(Role.PLAYER);
        }
        user = users.save(user);

        profiles.ensureProfile(user);

        return AppUser.from(user, attributes);
    }

    private static String avatarUrl(String discordId, String avatarHash) {
        if (avatarHash == null || avatarHash.isBlank()) {
            return null;
        }
        String extension = avatarHash.startsWith("a_") ? "gif" : "png";
        return "https://cdn.discordapp.com/avatars/%s/%s.%s"
                .formatted(discordId, avatarHash, extension);
    }

    /** Sized for the card's stats panel; animated banners stay animated. */
    private static String bannerUrl(String discordId, String bannerHash) {
        if (bannerHash == null || bannerHash.isBlank()) {
            return null;
        }
        String extension = bannerHash.startsWith("a_") ? "gif" : "png";
        return "https://cdn.discordapp.com/banners/%s/%s.%s?size=600"
                .formatted(discordId, bannerHash, extension);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
