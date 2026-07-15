package com.ailog.context;

import com.ailog.common.event.Event;
import com.ailog.common.event.EventType;
import com.ailog.common.event.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContextBuilderServiceTest {

    @Mock
    private ContextProvider mockProvider1;

    @Mock
    private ContextProvider mockProvider2;

    private ContextBuilderService service;

    @BeforeEach
    void setUp() {
        when(mockProvider1.name()).thenReturn("provider1");
        when(mockProvider2.name()).thenReturn("provider2");
        service = new ContextBuilderService(List.of(mockProvider1, mockProvider2));
    }

    @Test
    void shouldBuildContextPackageFromAllProviders() {
        Event event = new Event(UUID.randomUUID().toString(), EventType.LOG_ERROR, "order-service", "LogError",
                "error msg", Instant.now(), "source", Severity.HIGH, Map.of());

        when(mockProvider1.collect(event)).thenReturn(ContextData.success("provider1", Map.of("key", "val1")));
        when(mockProvider2.collect(event)).thenReturn(ContextData.success("provider2", Map.of("key", "val2")));

        ContextPackage result = service.build(event);

        assertNotNull(result);
        assertEquals(event.getId(), result.getEventId());
        assertSame(event, result.getOriginalEvent());
        assertEquals(2, result.getContexts().size());
    }

    @Test
    void shouldStillBuildWhenSomeProvidersFail() {
        Event event = new Event(UUID.randomUUID().toString(), EventType.LOG_ERROR, "order-service", "LogError",
                "error msg", Instant.now(), "source", Severity.HIGH, Map.of());

        when(mockProvider1.collect(event)).thenReturn(ContextData.failure("provider1", "error"));
        when(mockProvider2.collect(event)).thenReturn(ContextData.success("provider2", Map.of("key", "val")));

        ContextPackage result = service.build(event);

        assertNotNull(result);
        assertEquals(2, result.getContexts().size());
        assertFalse(result.getContexts().get("provider1").isSuccess());
        assertTrue(result.getContexts().get("provider2").isSuccess());
    }

    @Test
    void shouldHandleEmptyProviderList() {
        ContextBuilderService emptyService = new ContextBuilderService(List.of());
        Event event = new Event(UUID.randomUUID().toString(), EventType.LOG_ERROR, "s", "e",
                "m", Instant.now(), "src", Severity.LOW, Map.of());

        ContextPackage result = emptyService.build(event);
        assertNotNull(result);
        assertTrue(result.getContexts().isEmpty());
    }
}
