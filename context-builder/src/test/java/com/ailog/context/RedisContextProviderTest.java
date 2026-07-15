package com.ailog.context;

import com.ailog.common.event.Event;
import com.ailog.common.event.EventType;
import com.ailog.common.event.Severity;
import com.ailog.mcp.RedisTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisContextProviderTest {

    @Mock
    private RedisTool redisTool;

    @Test
    void shouldCollectRedisContext() {
        RedisContextProvider provider = new RedisContextProvider(redisTool);
        when(redisTool.memory()).thenReturn("used_memory_rss: 1048576");
        when(redisTool.clusterInfo()).thenReturn("redis_version: 7.0");

        Event event = new Event(UUID.randomUUID().toString(), EventType.REDIS_FAILURE, "redis", "MemoryAlert",
                "high memory", Instant.now(), "redis", Severity.MEDIUM, Map.of());

        ContextData result = provider.collect(event);

        assertTrue(result.isSuccess());
        assertEquals("redis", result.getProviderName());
        assertTrue(result.getData().containsKey("memory"));
        assertTrue(result.getData().containsKey("clusterInfo"));
    }

    @Test
    void shouldHandleException() {
        RedisContextProvider provider = new RedisContextProvider(redisTool);
        when(redisTool.memory()).thenThrow(new RuntimeException("Redis unavailable"));

        Event event = new Event(UUID.randomUUID().toString(), EventType.REDIS_FAILURE, "redis", "MemoryAlert",
                "error", Instant.now(), "redis", Severity.MEDIUM, Map.of());

        ContextData result = provider.collect(event);
        assertFalse(result.isSuccess());
    }
}
