package com.ailog.dashboard.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebSocketConfigTest {

    @Test
    void parsesConfiguredOriginsAndTrimsWhitespace() {
        assertArrayEquals(
                new String[]{"http://localhost:5173", "https://dashboard.example.com"},
                WebSocketConfig.parseAllowedOrigins(" http://localhost:5173, https://dashboard.example.com "));
    }

    @Test
    void rejectsBlankAndWildcardOrigins() {
        assertThrows(IllegalArgumentException.class, () -> WebSocketConfig.parseAllowedOrigins(" "));
        assertThrows(IllegalArgumentException.class, () -> WebSocketConfig.parseAllowedOrigins("*"));
    }
}
