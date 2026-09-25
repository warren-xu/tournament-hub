package com.warren.warrenament.auth;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived, single-use tickets that authenticate the auction WebSocket.
 * <p>
 * In production the frontend is served from Vercel and the socket goes straight to this
 * backend on another site, so the session cookie never reaches the handshake. The browser
 * instead fetches a ticket over REST (proxied through the frontend, so the cookie applies)
 * and presents it in the STOMP CONNECT frame.
 * <p>
 * Held in memory: one backend instance owns the auction, as with the simple broker.
 */
@Service
public class SocketTicketService {

    static final Duration TTL = Duration.ofSeconds(60);

    private record Ticket(AppUser user, Instant expiresAt) {
    }

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final Clock clock;

    public SocketTicketService() {
        this(Clock.systemUTC());
    }

    SocketTicketService(Clock clock) {
        this.clock = clock;
    }

    public String issue(AppUser user) {
        Instant now = clock.instant();
        // Unredeemed tickets (a tab closed mid-connect) are dropped on the next issue.
        tickets.values().removeIf(t -> t.expiresAt().isBefore(now));

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tickets.put(value, new Ticket(user, now.plus(TTL)));
        return value;
    }

    /** Consumes the ticket: a second redemption, or one after expiry, finds nothing. */
    public Optional<AppUser> redeem(String value) {
        if (value == null) {
            return Optional.empty();
        }
        Ticket ticket = tickets.remove(value);
        if (ticket == null || ticket.expiresAt().isBefore(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(ticket.user());
    }
}
