package com.example.ssl.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Value("${app.websocket.endpoint:/coins}")
    private String websocketEndpoint;

    private final UserWebSocketHandler userWebSocketHandler;
    private final WebSocketHandshakeInterceptor handshakeInterceptor;

    public WebSocketConfig(UserWebSocketHandler userWebSocketHandler,
                           WebSocketHandshakeInterceptor handshakeInterceptor) {
        this.userWebSocketHandler = userWebSocketHandler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(userWebSocketHandler, websocketEndpoint)
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins("*");
    }
}