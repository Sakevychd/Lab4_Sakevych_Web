package com.example.ssl.websocket;

import com.example.ssl.jwt.JwtValidator;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Component
public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtValidator jwtValidator;

    public WebSocketHandshakeInterceptor(JwtValidator jwtValidator) {
        this.jwtValidator = jwtValidator;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        try {
            String token = UriComponentsBuilder
                    .fromUri(request.getURI())
                    .build()
                    .getQueryParams()
                    .getFirst("token");

            if (token == null || token.isBlank()) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            JsonNode claims = jwtValidator.validateJwtAndGetClaims(token);

            String userId = getClaim(claims, "sub");
            String username = getClaim(claims, "name");

            if (userId == null || userId.isBlank()) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            attributes.put("token", token);
            attributes.put("userId", userId);
            attributes.put("username", username);
            attributes.put("claims", claims);

            return true;

        } catch (Exception e) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
    }

    private String getClaim(JsonNode claims, String name) {
        if (claims.has(name) && !claims.get(name).isNull()) {
            return claims.get(name).asText();
        }
        return null;
    }
}