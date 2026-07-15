package com.ailog.context;

import com.ailog.common.event.Event;
import com.ailog.common.event.EventType;
import com.ailog.common.event.Severity;
import com.ailog.mcp.MqTool;
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
class MqContextProviderTest {

    @Mock
    private MqTool mqTool;

    @Test
    void shouldCollectMqContext() {
        MqContextProvider provider = new MqContextProvider(mqTool);
        when(mqTool.lag("DEFAULT_CONSUMER")).thenReturn("client  connected");
        when(mqTool.retry("DEFAULT_CONSUMER")).thenReturn("Retry queue size: 5");

        Event event = new Event(UUID.randomUUID().toString(), EventType.MQ_LAG, "rocketmq", "ConsumerLag",
                "lag detected", Instant.now(), "rocketmq", Severity.MEDIUM, Map.of());

        ContextData result = provider.collect(event);

        assertTrue(result.isSuccess());
        assertEquals("mq", result.getProviderName());
        assertTrue(result.getData().containsKey("lag"));
        assertTrue(result.getData().containsKey("retry"));
    }

    @Test
    void shouldHandleException() {
        MqContextProvider provider = new MqContextProvider(mqTool);
        when(mqTool.lag(anyString())).thenThrow(new RuntimeException("MQ unavailable"));

        Event event = new Event(UUID.randomUUID().toString(), EventType.MQ_LAG, "rocketmq", "ConsumerLag",
                "error", Instant.now(), "rocketmq", Severity.MEDIUM, Map.of());

        ContextData result = provider.collect(event);
        assertFalse(result.isSuccess());
    }
}
