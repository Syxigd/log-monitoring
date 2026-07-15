package com.ailog.observe.collector;

import com.ailog.common.event.Event;
import com.ailog.common.event.EventType;
import com.ailog.common.event.Severity;
import com.ailog.mcp.MysqlTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
public class MysqlCollector implements EventCollector {

    private static final Logger log = LoggerFactory.getLogger(MysqlCollector.class);

    private final MysqlTool mysqlTool;

    public MysqlCollector(MysqlTool mysqlTool) {
        this.mysqlTool = mysqlTool;
    }

    @Override
    public String name() {
        return "mysql";
    }

    @Override
    public List<Event> collect() {
        List<Event> events = new ArrayList<>();

        try {
            String slow = mysqlTool.slowSql();
            if (slow != null && !slow.isBlank() && !slow.startsWith("Error") && !slow.startsWith("0 rows")) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("details", slow);
                events.add(new Event(
                        UUID.randomUUID().toString(),
                        EventType.DB_SLOW_QUERY,
                        "mysql",
                        "SlowQuery",
                        slow,
                        Instant.now(),
                        "mysql",
                        Severity.MEDIUM,
                        payload
                ));
            }
        } catch (Exception e) {
            log.error("MySQL collector failed: {}", e.getMessage());
        }

        return events;
    }
}
