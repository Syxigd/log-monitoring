package com.ailog.rootcause.rule;

import com.ailog.common.event.Event;
import com.ailog.common.event.EventType;
import com.ailog.common.event.Severity;
import com.ailog.context.ContextData;
import com.ailog.context.ContextPackage;
import com.ailog.rootcause.RootCause;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DbPerformanceRuleTest {

    private final DbPerformanceRule rule = new DbPerformanceRule();

    @Test
    void shouldDetectDbPerformanceFromSlowQuery() {
        Event event = new Event(UUID.randomUUID().toString(), EventType.DB_SLOW_QUERY, "mysql",
                "SlowQuery", "5s SELECT * FROM orders",
                Instant.now(), "mysql", Severity.MEDIUM, Map.of());

        ContextPackage pkg = new ContextPackage(event);
        pkg.addContext(ContextData.success("db", Map.of(
                "slowSql", "5s SELECT * FROM orders",
                "locks", "1  root  Query  SELECT ..."
        )));

        assertTrue(rule.evaluate(pkg));
        assertEquals(RootCause.DB_PERFORMANCE, rule.rootCause());
    }

    @Test
    void shouldNotDetectWhenNoSlowQuery() {
        Event event = new Event(UUID.randomUUID().toString(), EventType.LOG_ERROR, "order-service",
                "NPE", "error", Instant.now(), "source", Severity.HIGH, Map.of());

        ContextPackage pkg = new ContextPackage(event);
        assertFalse(rule.evaluate(pkg));
    }
}
