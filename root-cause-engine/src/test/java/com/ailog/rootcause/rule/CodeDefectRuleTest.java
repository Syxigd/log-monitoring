package com.ailog.rootcause.rule;

import com.ailog.common.event.Event;
import com.ailog.common.event.EventType;
import com.ailog.common.event.Severity;
import com.ailog.context.ContextData;
import com.ailog.context.ContextPackage;
import com.ailog.rootcause.RootCause;
import com.ailog.rootcause.RootCauseReport;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CodeDefectRuleTest {

    private final CodeDefectRule rule = new CodeDefectRule();

    @Test
    void shouldDetectCodeDefectWhenExceptionAndGitChangePresent() {
        Event event = new Event(UUID.randomUUID().toString(), EventType.LOG_ERROR, "order-service",
                "NullPointerException", "at com.ailog.order.OrderService.createOrder",
                Instant.now(), "source", Severity.HIGH, Map.of());

        ContextPackage pkg = new ContextPackage(event);
        pkg.addContext(ContextData.success("git", Map.of(
                "recentCommits", "abc123 Fixed NPE in OrderService",
                "recentChanges", "OrderService.java"
        )));

        assertTrue(rule.evaluate(pkg));
        assertEquals(RootCause.CODE_DEFECT, rule.rootCause());

        RootCauseReport report = rule.execute(pkg);
        assertEquals(RootCause.CODE_DEFECT, report.getRootCause());
        assertFalse(report.getEvidence().isEmpty());
        assertEquals("order-service", report.getAffectedService());
    }

    @Test
    void shouldNotDetectWhenNoExceptionType() {
        Event event = new Event(UUID.randomUUID().toString(), EventType.LOG_ERROR, "order-service",
                null, null, Instant.now(), "source", Severity.HIGH, Map.of());

        ContextPackage pkg = new ContextPackage(event);
        pkg.addContext(ContextData.success("git", Map.of("recentCommits", "abc123")));

        assertFalse(rule.evaluate(pkg));
    }

    @Test
    void shouldNotDetectWhenNoGitContext() {
        Event event = new Event(UUID.randomUUID().toString(), EventType.LOG_ERROR, "order-service",
                "NullPointerException", "error", Instant.now(), "source", Severity.HIGH, Map.of());

        ContextPackage pkg = new ContextPackage(event);
        pkg.addContext(ContextData.failure("git", "Git unavailable"));

        assertFalse(rule.evaluate(pkg));
    }
}
