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

class ResourceExhaustionRuleTest {

    private final ResourceExhaustionRule rule = new ResourceExhaustionRule();

    @Test
    void shouldDetectResourceExhaustionFromDockerOom() {
        Event event = new Event(UUID.randomUUID().toString(), EventType.DOCKER_CRASH, "order-service",
                "ContainerCrash", "Container exited with OOMKilled",
                Instant.now(), "docker", Severity.HIGH, Map.of("containerId", "abc"));

        ContextPackage pkg = new ContextPackage(event);
        pkg.addContext(ContextData.success("docker", Map.of("logs", "2025-01-01 OOMKilled java process killed")));

        assertTrue(rule.evaluate(pkg));
        assertEquals(RootCause.RESOURCE_EXHAUSTION, rule.rootCause());

        RootCauseReport report = rule.execute(pkg);
        assertEquals(RootCause.RESOURCE_EXHAUSTION, report.getRootCause());
        assertFalse(report.getEvidence().isEmpty());
    }

    @Test
    void shouldDetectResourceExhaustionFromRedisMemory() {
        Event event = new Event(UUID.randomUUID().toString(), EventType.REDIS_FAILURE, "redis",
                "MemoryAlert", "used_memory_rss high",
                Instant.now(), "redis", Severity.MEDIUM, Map.of());

        ContextPackage pkg = new ContextPackage(event);
        pkg.addContext(ContextData.success("redis", Map.of("memory", "used_memory_rss: 2097152000")));

        assertTrue(rule.evaluate(pkg));
    }

    @Test
    void shouldNotDetectWhenNoResourceIndicators() {
        Event event = new Event(UUID.randomUUID().toString(), EventType.LOG_ERROR, "order-service",
                "NPE", "error", Instant.now(), "source", Severity.HIGH, Map.of());

        ContextPackage pkg = new ContextPackage(event);
        assertFalse(rule.evaluate(pkg));
    }
}
