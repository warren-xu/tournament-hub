package com.warren.warrenament.config;

import com.warren.warrenament.auth.AppUser;
import com.warren.warrenament.auth.Role;
import com.warren.warrenament.auth.User;
import com.warren.warrenament.auth.UserRepository;
import com.warren.warrenament.profile.PlayerProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Dev-profile login shortcut. Testing an auction needs several distinct signed-in captains,
 * and creating a Discord account per test captain is not workable.
 * <p>
 * Only registered under the {@code dev} profile - it grants a session to anyone who asks.
 */
@RestController
@RequestMapping("/api/dev")
@Profile("dev")
public class DevLoginController {

    private final UserRepository users;
    private final PlayerProfileService profiles;
    private final HttpSessionSecurityContextRepository contextRepository =
            new HttpSessionSecurityContextRepository();

    public DevLoginController(UserRepository users, PlayerProfileService profiles) {
        this.users = users;
        this.profiles = profiles;
    }

    @PostMapping("/login")
    @Transactional
    public Map<String, Object> login(@RequestParam String username,
                                     @RequestParam(defaultValue = "false") boolean admin,
                                     HttpServletRequest request,
                                     HttpServletResponse response) {
        String discordId = "dev-" + username.toLowerCase();

        User user = users.findByDiscordId(discordId).orElseGet(User::new);
        user.setDiscordId(discordId);
        user.setUsername(username);
        user.setRole(admin ? Role.ADMIN : Role.PLAYER);
        user = users.save(user);
        profiles.ensureProfile(user);

        AppUser principal = AppUser.from(user, Map.of("id", discordId));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);

        return Map.of(
                "userId", user.getId(),
                "username", user.getUsername(),
                "role", user.getRole().name());
    }
}
