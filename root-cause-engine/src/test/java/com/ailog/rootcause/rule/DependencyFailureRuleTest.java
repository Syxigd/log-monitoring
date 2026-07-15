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

class DependencyFailureRuleTest {

    private final DependencyFailureRule rule = new DependencyFailureRule();

    @Test
    void shouldDetectDependencyFailureFromMqLag() {
        Event event = new Event(UUID.randomUUID().toString(), EventType.MQ_LAG, "rocketmq",
                "ConsumerLag", "lag detected",
                Instant.now(), "rocketmq", Severity.MEDIUM, Map.of());

        ContextPackage pkg = new ContextPackage(event);
        pkg.addContext(ContextData.success("mq", Map.of(
                "lag", "consumer disconnected",
                "retry", "Retry queue size: 50"
        )));

        assertTrue(rule.evaluate(pkg));
        assertEquals(RootCause.DEPENDENCY_FAILURE, rule.rootCause());
    }

    @Test
    void shouldNotDetectWhenNoMqLag() {
        Event event = new Event(UUID.randomUUID().toString(), EventType.LOG_ERROR, "order-service",
                "NPE", "error", Instant.now(), "source", Severity.HIGH, Map.of());

        ContextPackage pkg = new ContextPackage(event);
        assertFalse(rule.evaluate(pkg));
    }
}
