package com.ailog.context;

import com.ailog.common.event.Event;
import com.ailog.common.event.EventType;
import com.ailog.common.event.Severity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EnvContextProviderTest {

    private final EnvContextProvider provider = new EnvContextProvider();

    @Test
    void shouldCollectEnvContext() {
        Event event = new Event(UUID.randomUUID().toString(), EventType.LOG_ERROR, "order-service", "LogError",
                "error", Instant.now(), "source", Severity.HIGH, Map.of());

        ContextData result = provider.collect(event);

        assertTrue(result.isSuccess());
        assertEquals("env", result.getProviderName());
        assertTrue(result.getData().containsKey("javaVersion"));
        assertTrue(result.getData().containsKey("osName"));
        assertTrue(result.getData().containsKey("availableProcessors"));
    }

    @Test
    void shouldWorkWithAnyEvent() {
        ContextData result = provider.collect(null);
        assertTrue(result.isSuccess());
        assertTrue(result.getData().containsKey("javaVersion"));
    }
}
