package com.ailog.dashboard.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final String[] allowedOrigins;

    public WebSocketConfig(
            @Value("${dashboard.allowed-origins:http://localhost:5173}") String allowedOrigins) {
        this.allowedOrigins = parseAllowedOrigins(allowedOrigins);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new EventWebSocketHandler(), "/ws/events")
                .setAllowedOrigins(allowedOrigins);
    }

    static String[] parseAllowedOrigins(String configuredOrigins) {
        if (configuredOrigins == null || configuredOrigins.isBlank()) {
            throw new IllegalArgumentException("dashboard.allowed-origins must not be blank");
        }
        String[] origins = Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
        if (origins.length == 0 || Arrays.stream(origins).anyMatch("*"::equals)) {
            throw new IllegalArgumentException("Wildcard WebSocket origins are not allowed");
        }
        return origins;
    }
}
