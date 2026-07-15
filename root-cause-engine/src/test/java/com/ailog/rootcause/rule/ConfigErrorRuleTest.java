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

class ConfigErrorRuleTest {

    private final ConfigErrorRule rule = new ConfigErrorRule();

    @Test
    void shouldDetectConfigErrorFromErrorMessage() {
        Event event = new Event(UUID.randomUUID().toString(), EventType.LOG_ERROR, "order-service",
                "FileNotFoundException", "application.yml not found",
                Instant.now(), "source", Severity.HIGH, Map.of());

        ContextPackage pkg = new ContextPackage(event);
        pkg.addContext(ContextData.success("env", Map.of()));

        assertTrue(rule.evaluate(pkg));
        assertEquals(RootCause.CONFIG_ERROR, rule.rootCause());
    }

    @Test
    void shouldNotDetectWhenNoConfigKeywords() {
        Event event = new Event(UUID.randomUUID().toString(), EventType.LOG_ERROR, "order-service",
                "NullPointerException", "null reference",
                Instant.now(), "source", Severity.HIGH, Map.of());

        ContextPackage pkg = new ContextPackage(event);
        assertFalse(rule.evaluate(pkg));
    }
}
