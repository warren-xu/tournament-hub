package com.warren.warrenament.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SocketTicketServiceTest {

    private static final AppUser CAPTAIN =
            new AppUser(1L, "dev-captain", "captain", null, Role.PLAYER, Map.of());

    /** A clock the test can move forward. */
    private static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final TestClock clock = new TestClock();
    private final SocketTicketService tickets = new SocketTicketService(clock);

    @Test
    @DisplayName("a ticket identifies its user exactly once")
    void singleUse() {
        String ticket = tickets.issue(CAPTAIN);

        assertThat(tickets.redeem(ticket)).contains(CAPTAIN);
        assertThat(tickets.redeem(ticket)).isEmpty();
    }

    @Test
    @DisplayName("a ticket is worthless once it expires")
    void expires() {
        String ticket = tickets.issue(CAPTAIN);
        clock.now = clock.now.plus(SocketTicketService.TTL).plus(Duration.ofSeconds(1));

        assertThat(tickets.redeem(ticket)).isEmpty();
    }

    @Test
    @DisplayName("unknown or missing tickets identify nobody")
    void unknown() {
        assertThat(tickets.redeem("made-up")).isEmpty();
        assertThat(tickets.redeem(null)).isEmpty();
    }

    @Test
    @DisplayName("every ticket is distinct")
    void distinct() {
        assertThat(tickets.issue(CAPTAIN)).isNotEqualTo(tickets.issue(CAPTAIN));
    }
}
