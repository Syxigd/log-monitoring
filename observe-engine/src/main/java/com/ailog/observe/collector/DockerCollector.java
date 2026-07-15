package com.ailog.observe.collector;

import com.ailog.common.event.Event;
import com.ailog.common.event.EventType;
import com.ailog.common.event.Severity;
import com.ailog.mcp.DockerTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
public class DockerCollector implements EventCollector {

    private static final Logger log = LoggerFactory.getLogger(DockerCollector.class);

    private final DockerTool dockerTool;
    private final Map<String, String> previousStates = new HashMap<>();

    public DockerCollector(DockerTool dockerTool) {
        this.dockerTool = dockerTool;
    }

    @Override
    public String name() {
        return "docker";
    }

    @Override
    public List<Event> collect() {
        List<Event> events = new ArrayList<>();
        try {
            String ps = dockerTool.ps();
            if (ps == null || ps.isBlank()) return events;

            String[] lines = ps.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.isBlank()) continue;
                String[] parts = line.split("\\s+", 3);
                if (parts.length < 3) continue;

                String containerId = parts[0];
                String state = parts[2];
                String previous = previousStates.get(containerId);

                if ("exited".equalsIgnoreCase(state) || "unhealthy".equalsIgnoreCase(state)) {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("containerId", containerId);
                    payload.put("state", state);
                    payload.put("line", line);
                    events.add(new Event(
                            UUID.randomUUID().toString(),
                            EventType.DOCKER_CRASH,
                            "docker",
                            "ContainerCrash",
                            "Container " + containerId.substring(0, 12) + " state: " + state,
                            Instant.now(),
                            "docker",
                            Severity.HIGH,
                            payload
                    ));
                }
                previousStates.put(containerId, state);
            }
        } catch (Exception e) {
            log.error("Docker collector failed: {}", e.getMessage());
        }
        return events;
    }
}
