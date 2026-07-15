package com.ailog.context;

import com.ailog.common.event.Event;
import com.ailog.common.event.EventType;
import com.ailog.common.event.Severity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CodeContextProviderTest {

    private final CodeContextProvider provider = new CodeContextProvider("src/main/java");

    @Test
    void shouldHandleEmptyErrorMessage() {
        Event event = new Event(UUID.randomUUID().toString(), EventType.LOG_ERROR, "order-service", "NullPointerException",
                null, Instant.now(), "source", Severity.HIGH, Map.of());

        ContextData result = provider.collect(event);

        assertNotNull(result);
        assertEquals("code", result.getProviderName());
    }

    @Test
    void shouldNotThrowOnAnyInput() {
        Event event = new Event(UUID.randomUUID().toString(), EventType.LOG_ERROR, "order-service", "NullPointerException",
                "at com.ailog.order.OrderService.createOrder(OrderService.java:42)",
                Instant.now(), "source", Severity.HIGH, Map.of());

        ContextData result = provider.collect(event);

        assertNotNull(result);
    }
}
