package com.ailog.observe.collector;

import com.ailog.common.event.Event;
import com.ailog.common.event.EventType;
import com.ailog.common.event.Severity;
import com.ailog.mcp.MqTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
public class MqCollector implements EventCollector {

    private static final Logger log = LoggerFactory.getLogger(MqCollector.class);

    private final MqTool mqTool;

    public MqCollector(MqTool mqTool) {
        this.mqTool = mqTool;
    }

    @Override
    public String name() {
        return "mq";
    }

    @Override
    public List<Event> collect() {
        List<Event> events = new ArrayList<>();

        try {
            String retry = mqTool.retry("DEFAULT_CONSUMER");
            if (retry != null && !retry.startsWith("Error") && !retry.endsWith("0")) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("details", retry);
                events.add(new Event(
                        UUID.randomUUID().toString(),
                        EventType.MQ_LAG,
                        "rocketmq",
                        "ConsumerLag",
                        retry,
                        Instant.now(),
                        "rocketmq",
                        Severity.MEDIUM,
                        payload
                ));
            }
        } catch (Exception e) {
            log.error("MQ collector failed: {}", e.getMessage());
        }

        return events;
    }
}
