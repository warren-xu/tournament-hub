package com.warren.warrenament.config;

import com.warren.warrenament.auth.SocketTicketService;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** STOMP CONNECT header carrying a ticket from {@code POST /api/ws-ticket}. */
    static final String TICKET_HEADER = "ticket";

    private final SocketTicketService tickets;

    public WebSocketConfig(SocketTicketService tickets) {
        this.tickets = tickets;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // In-memory broker: one backend instance owns one auction. Swap for a relay
        // only if this ever needs to run on more than one node.
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // The browser connects here directly, not through the frontend's proxy. Locally
        // the session cookie still reaches the handshake (cookies ignore ports); deployed,
        // the frontend is another site and bidders authenticate with a ticket instead.
        // Any origin may connect: without a ticket or cookie a connection can only watch.
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
                    return message;
                }
                String ticket = accessor.getFirstNativeHeader(TICKET_HEADER);
                if (ticket == null) {
                    return message; // Spectator, or a cookie-authenticated handshake.
                }
                // A bad ticket fails loudly rather than silently demoting a captain to a
                // spectator whose bids go nowhere; the client fetches a fresh one and retries.
                var user = tickets.redeem(ticket).orElseThrow(() ->
                        new MessageDeliveryException("Invalid or expired socket ticket"));
                accessor.setUser(new UsernamePasswordAuthenticationToken(
                        user, null, user.getAuthorities()));
                return message;
            }
        });
    }
}
