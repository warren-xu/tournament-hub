package com.warren.warrenament.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

/**
 * @param adminDiscordIds Discord ids promoted to ADMIN on login. Put your own id here so you
 *                        can run the draft; everyone else logs in as a PLAYER.
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(Set<String> adminDiscordIds) {

    public AuthProperties {
        adminDiscordIds = adminDiscordIds == null ? Set.of() : adminDiscordIds;
    }
}
