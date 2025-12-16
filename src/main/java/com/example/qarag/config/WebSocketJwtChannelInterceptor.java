package com.example.qarag.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WebSocketJwtChannelInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;

    public WebSocketJwtChannelInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            List<String> authorization = accessor.getNativeHeader("Authorization");
            String token = null;
            if (authorization != null && !authorization.isEmpty()) {
                String authHeader = authorization.get(0);
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    token = authHeader.substring(7);
                }
            }

            if (token != null && jwtUtil.validateToken(token)) {
                Long userId = jwtUtil.getUserIdFromToken(token);
                // Store userId in WebSocket session attributes
                accessor.getSessionAttributes().put("userId", userId);
                
                // Set the User Principal so convertAndSendToUser works
                accessor.setUser(new java.security.Principal() {
                    @Override
                    public String getName() {
                        return String.valueOf(userId);
                    }
                });
            } else {
                // If token is invalid or missing, deny connection
                throw new IllegalArgumentException("Unauthorized connection");
            }
        }
        return message;
    }
}
