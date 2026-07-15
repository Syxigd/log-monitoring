package com.ailog.context;

import com.ailog.common.event.Event;
import com.ailog.common.event.EventType;
import com.ailog.common.event.Severity;
import com.ailog.mcp.DockerTool;
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
class DockerContextProviderTest {

    @Mock
    private DockerTool dockerTool;

    @Test
    void shouldCollectDockerContext() {
        DockerContextProvider provider = new DockerContextProvider(dockerTool);

        when(dockerTool.ps()).thenReturn("abc123  my-app  running");
        when(dockerTool.logs("abc123", 50)).thenReturn("log line 1\nlog line 2");

        Event event = new Event(UUID.randomUUID().toString(), EventType.DOCKER_CRASH, "docker", "ContainerCrash",
                "Container abc123 state: exited", Instant.now(), "docker", Severity.HIGH, Map.of("containerId", "abc123"));

        ContextData result = provider.collect(event);

        assertTrue(result.isSuccess());
        assertEquals("docker", result.getProviderName());
        assertTrue(result.getData().containsKey("ps"));
        assertTrue(result.getData().containsKey("logs"));
        verify(dockerTool).ps();
    }

    @Test
    void shouldReturnFailureWhenDockerToolThrows() {
        DockerContextProvider provider = new DockerContextProvider(dockerTool);
        when(dockerTool.ps()).thenThrow(new RuntimeException("Docker not available"));

        Event event = new Event(UUID.randomUUID().toString(), EventType.DOCKER_CRASH, "docker", "ContainerCrash",
                "error", Instant.now(), "docker", Severity.HIGH, Map.of());

        ContextData result = provider.collect(event);

        assertFalse(result.isSuccess());
        assertEquals("docker", result.getProviderName());
        assertNotNull(result.getErrorMessage());
    }
}
