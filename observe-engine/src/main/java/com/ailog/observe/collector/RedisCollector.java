package com.ailog.observe.collector;

import com.ailog.common.event.Event;
import com.ailog.common.event.EventType;
import com.ailog.common.event.Severity;
import com.ailog.mcp.RedisTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
public class RedisCollector implements EventCollector {

    private static final Logger log = LoggerFactory.getLogger(RedisCollector.class);

    private final RedisTool redisTool;

    public RedisCollector(RedisTool redisTool) {
        this.redisTool = redisTool;
    }

    @Override
    public String name() {
        return "redis";
    }

    @Override
    public List<Event> collect() {
        List<Event> events = new ArrayList<>();

        try {
            String mem = redisTool.memory();
            if (mem != null && mem.contains("used_memory_rss")) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("details", mem);
                events.add(new Event(
                        UUID.randomUUID().toString(),
                        EventType.REDIS_FAILURE,
                        "redis",
                        "MemoryAlert",
                        mem,
                        Instant.now(),
                        "redis",
                        Severity.MEDIUM,
                        payload
                ));
            }
        } catch (Exception e) {
            log.error("Redis collector failed: {}", e.getMessage());
        }

        return events;
    }
}
