package com.twocold.jrag.config;

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
                String authHeader = authorization.getFirst();
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    token = authHeader.substring(7);
                }
            }

            if (token != null && jwtUtil.validateToken(token)) {
                Long userId = jwtUtil.getUserIdFromToken(token);
                // 将 userId 存储在 WebSocket 会话属性中
                accessor.getSessionAttributes().put("userId", userId);
                
                // 设置 User Principal，以便 convertAndSendToUser 能够正常工作
                accessor.setUser(new java.security.Principal() {
                    @Override
                    public String getName() {
                        return String.valueOf(userId);
                    }
                });
            } else {
                // 如果令牌无效或缺失，则拒绝连接
                throw new IllegalArgumentException("未授权的连接");
            }
        }
        return message;
    }
}
