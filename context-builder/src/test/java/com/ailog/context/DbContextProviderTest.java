package com.ailog.context;

import com.ailog.common.event.Event;
import com.ailog.common.event.EventType;
import com.ailog.common.event.Severity;
import com.ailog.mcp.MysqlTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DbContextProviderTest {

    @Mock
    private MysqlTool mysqlTool;

    @Test
    void shouldCollectDbContext() {
        DbContextProvider provider = new DbContextProvider(mysqlTool);

        when(mysqlTool.slowSql()).thenReturn("5s  SELECT * FROM orders");
        when(mysqlTool.locks()).thenReturn("1  root  Query  SELECT ...");

        Event event = new Event(UUID.randomUUID().toString(), EventType.DB_SLOW_QUERY, "mysql", "SlowQuery",
                "5s query", Instant.now(), "mysql", Severity.MEDIUM, Map.of());

        ContextData result = provider.collect(event);

        assertTrue(result.isSuccess());
        assertEquals("db", result.getProviderName());
        assertTrue(result.getData().containsKey("slowSql"));
        assertTrue(result.getData().containsKey("locks"));
    }

    @Test
    void shouldHandleException() {
        DbContextProvider provider = new DbContextProvider(mysqlTool);
        when(mysqlTool.slowSql()).thenThrow(new RuntimeException("DB down"));

        Event event = new Event(UUID.randomUUID().toString(), EventType.DB_SLOW_QUERY, "mysql", "SlowQuery",
                "error", Instant.now(), "mysql", Severity.MEDIUM, Map.of());

        ContextData result = provider.collect(event);

        assertFalse(result.isSuccess());
    }
}
