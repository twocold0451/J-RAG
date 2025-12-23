package com.example.qarag.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketJwtChannelInterceptor webSocketJwtChannelInterceptor;

    public WebSocketConfig(WebSocketJwtChannelInterceptor webSocketJwtChannelInterceptor) {
        this.webSocketJwtChannelInterceptor = webSocketJwtChannelInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册 "/ws" 端点，并启用 SockJS 回退选项。
        // 此处使用 SockJS 是为了实现更广泛的浏览器兼容性。
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // 考虑在生产环境中对此进行限制
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketJwtChannelInterceptor);
    }
}