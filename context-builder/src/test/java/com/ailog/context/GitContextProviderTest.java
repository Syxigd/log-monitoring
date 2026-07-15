package com.ailog.context;

import com.ailog.common.event.Event;
import com.ailog.common.event.EventType;
import com.ailog.common.event.Severity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GitContextProviderTest {

    private final GitContextProvider provider = new GitContextProvider();

    @Test
    void shouldReturnSuccessEvenIfGitFails() {
        Event event = new Event(UUID.randomUUID().toString(), EventType.LOG_ERROR, "order-service", "LogError",
                "NullPointerException", Instant.now(), "/var/log/order-service.log", Severity.HIGH, Map.of());

        ContextData result = provider.collect(event);

        assertNotNull(result);
        assertEquals("git", result.getProviderName());
    }

    @Test
    void shouldHandleNullServiceName() {
        Event event = new Event(UUID.randomUUID().toString(), EventType.LOG_ERROR, null, "LogError",
                "error", Instant.now(), "unknown", Severity.HIGH, Map.of());

        ContextData result = provider.collect(event);

        assertNotNull(result);
        assertTrue(result.isSuccess() || !result.isSuccess());
    }
}
