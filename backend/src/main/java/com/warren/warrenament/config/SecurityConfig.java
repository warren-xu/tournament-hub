package com.warren.warrenament.config;

import com.warren.warrenament.auth.DiscordOAuth2UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** Where Discord sends the browser back to after a successful login. */
    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            DiscordOAuth2UserService discordUserService) throws Exception {
        http
                // The browser talks to Next.js, which proxies REST through to here, so the
                // session cookie rides along. The auction WebSocket connects directly and is
                // authenticated by a ticket from /api/ws-ticket (see WebSocketConfig).
                //
                // CSRF is off because there is no cookie-authenticated state-changing form post
                // that isn't JSON from our own origin. Turn it back on (CookieCsrfTokenRepository
                // + an X-XSRF-TOKEN header from the client) before exposing this publicly.
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health/**",
                                "/api/me",
                                "/api/dev/**",
                                "/login/**",
                                "/oauth2/**",
                                "/ws/**",
                                "/error").permitAll()
                        // Browsing the hub does not require an account; acting does.
                        .requestMatchers(HttpMethod.GET,
                                "/api/agents",
                                "/api/ranks",
                                "/api/player-cards",
                                "/api/profiles",
                                "/api/profiles/*",
                                "/api/tournaments/**",
                                "/api/teams/**",
                                "/api/auctions/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(userInfo -> userInfo.userService(discordUserService))
                        .defaultSuccessUrl(frontendUrl, true)
                        .failureUrl(frontendUrl + "?login=failed"))
                .logout(logout -> logout
                        .logoutUrl("/api/logout")
                        .logoutSuccessUrl(frontendUrl))
                // A SPA wants a 401 to branch on, not a redirect to a login page.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));

        return http.build();
    }
}
