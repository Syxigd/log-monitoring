package com.ailog.rootcause;

import com.ailog.common.event.Event;
import com.ailog.common.event.EventType;
import com.ailog.common.event.Severity;
import com.ailog.context.ContextData;
import com.ailog.context.ContextPackage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RootCauseEngineServiceTest {

    @Test
    void shouldReturnCodeDefectWhenLayer1Hits() {
        Event event = new Event(UUID.randomUUID().toString(), EventType.LOG_ERROR, "order-service",
                "NullPointerException", "at OrderService.java",
                Instant.now(), "source", Severity.HIGH, Map.of());

        ContextPackage pkg = new ContextPackage(event);
        pkg.addContext(ContextData.success("git", Map.of("recentCommits", "abc123")));

        RootCauseEngineService service = new RootCauseEngineService(new java.util.ArrayList<>());
        // We can't easily test with Spring injection, so use reflection to set analyzers
        service.getClass().getDeclaredFields()[0].setAccessible(true);
        try {
            service.getClass().getDeclaredFields()[0].set(service,
                    java.util.List.of(new com.ailog.rootcause.rule.CodeDefectRule()));
        } catch (Exception e) {
            fail("Failed to inject analyzer");
        }

        RootCauseReport report = service.analyze(pkg);
        assertEquals(RootCause.CODE_DEFECT, report.getRootCause());
    }

    @Test
    void shouldReturnUnknownWhenNoRuleMatches() {
        Event event = new Event(UUID.randomUUID().toString(), EventType.LOG_ERROR, "unknown",
                null, null, Instant.now(), "source", Severity.LOW, Map.of());

        ContextPackage pkg = new ContextPackage(event);

        RootCauseEngineService service = new RootCauseEngineService(java.util.List.of());
        RootCauseReport report = service.analyze(pkg);

        assertEquals(RootCause.UNKNOWN, report.getRootCause());
    }

    @Test
    void shouldReturnDbPerformanceWhenNoLayer1ButLayer2Matches() {
        Event event = new Event(UUID.randomUUID().toString(), EventType.DB_SLOW_QUERY, "mysql",
                "SlowQuery", "5s query", Instant.now(), "mysql", Severity.MEDIUM, Map.of());

        ContextPackage pkg = new ContextPackage(event);
        pkg.addContext(ContextData.success("db", Map.of("slowSql", "5s SELECT * FROM orders")));

        RootCauseEngineService service = new RootCauseEngineService(
                java.util.List.of(new com.ailog.rootcause.rule.DbPerformanceRule()));
        RootCauseReport report = service.analyze(pkg);

        assertEquals(RootCause.DB_PERFORMANCE, report.getRootCause());
    }
}
