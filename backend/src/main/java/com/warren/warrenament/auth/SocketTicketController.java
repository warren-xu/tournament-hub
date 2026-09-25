package com.warren.warrenament.auth;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SocketTicketController {

    public record TicketView(String ticket) {
    }

    private final SocketTicketService tickets;

    public SocketTicketController(SocketTicketService tickets) {
        this.tickets = tickets;
    }

    /** Signed-in only (security config); spectators connect to the socket without one. */
    @PostMapping("/ws-ticket")
    public TicketView issue(@AuthenticationPrincipal AppUser user) {
        return new TicketView(tickets.issue(user));
    }
}
