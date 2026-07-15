# Context Builder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the context-builder module that receives Event from observe-engine, collects multi-source context data (Docker/MySQL/Redis/MQ/Git/Code/Env), and produces a ContextPackage for downstream analysis.

**Architecture:** Event-driven — ContextBuilderService subscribes to EventBus, dispatches each Event to 7 independent ContextProvider implementations, each using existing MCP tools (DockerTool/MysqlTool/RedisTool/MqTool) or external CLIs (git/filesystem), and assembles results into a ContextPackage.

**Tech Stack:** Spring Boot 3.4.4, Java 21, Maven, JUnit 5 + Mockito

---

## File Structure

```
common/src/main/java/com/ailog/common/event/
  └── SimpleEventBus.java            ← NEW: EventBus impl (needed dependency)

context-builder/src/main/java/com/ailog/context/
  ├── ContextPackage.java             ← NEW: output model
  ├── ContextData.java                ← NEW: per-provider result wrapper
  ├── ContextProvider.java            ← NEW: provider interface
  ├── ContextBuilderService.java      ← NEW: orchestrator, subscribes to EventBus
  ├── DockerContextProvider.java      ← NEW: uses DockerTool
  ├── DbContextProvider.java          ← NEW: uses MysqlTool (slow sql, locks)
  ├── RedisContextProvider.java       ← NEW: uses RedisTool (memory, keys)
  ├── MqContextProvider.java          ← NEW: uses MqTool (lag, retry)
  ├── GitContextProvider.java         ← NEW: git CLI calls
  ├── CodeContextProvider.java        ← NEW: file system reads based on errorMessage
  └── EnvContextProvider.java         ← NEW: JVM/OS system properties

context-builder/src/test/java/com/ailog/context/
  ├── ContextBuilderServiceTest.java  ← NEW
  ├── DockerContextProviderTest.java  ← NEW
  ├── DbContextProviderTest.java      ← NEW
  ├── RedisContextProviderTest.java   ← NEW
  ├── MqContextProviderTest.java      ← NEW
  ├── GitContextProviderTest.java     ← NEW
  ├── CodeContextProviderTest.java    ← NEW
  └── EnvContextProviderTest.java     ← NEW
```

---

### Task 0: Prerequisite — SimpleEventBus in common module

The project has only `EventBus` interface but no implementation. ContextBuilderService needs a concrete bean to subscribe to.

**Files:**
- Create: `common/src/main/java/com/ailog/common/event/SimpleEventBus.java`
- Modify: none
- Test: none (thin wiring — tested implicitly by ContextBuilderServiceTest)

- [ ] **Step 1: Create SimpleEventBus.java**

```java
package com.ailog.common.event;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SimpleEventBus implements EventBus {

    private final Map<EventType, List<EventHandler>> handlers = new ConcurrentHashMap<>();

    @Override
    public void publish(Event event) {
        List<EventHandler> list = handlers.get(event.getType());
        if (list == null) return;
        for (EventHandler handler : list) {
            try {
                handler.handle(event);
            } catch (Exception e) {
                // log and continue — one bad handler should not break others
            }
        }
    }

    @Override
    public void subscribe(EventType type, EventHandler handler) {
        handlers.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(handler);
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn clean compile -f pom.xml -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add common/src/main/java/com/ailog/common/event/SimpleEventBus.java
git commit -m "feat: add SimpleEventBus implementation"
```

---

### Task 1: ContextPackage and ContextData models

**Files:**
- Create: `context-builder/src/main/java/com/ailog/context/ContextData.java`
- Create: `context-builder/src/main/java/com/ailog/context/ContextPackage.java`
- Test: none (pure POJOs, no logic)

- [ ] **Step 1: Create ContextData.java**

```java
package com.ailog.context;

import java.util.Map;

public class ContextData {

    private final String providerName;
    private final Map<String, Object> data;
    private final boolean success;
    private final String errorMessage;

    public ContextData(String providerName, Map<String, Object> data, boolean success, String errorMessage) {
        this.providerName = providerName;
        this.data = data;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public static ContextData success(String providerName, Map<String, Object> data) {
        return new ContextData(providerName, data, true, null);
    }

    public static ContextData failure(String providerName, String errorMessage) {
        return new ContextData(providerName, Map.of(), false, errorMessage);
    }

    public String getProviderName() { return providerName; }
    public Map<String, Object> getData() { return data; }
    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }
}
```

- [ ] **Step 2: Create ContextPackage.java**

```java
package com.ailog.context;

import com.ailog.common.event.Event;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class ContextPackage {

    private final String eventId;
    private final Event originalEvent;
    private final Map<String, ContextData> contexts = new HashMap<>();
    private final Instant collectedAt;

    public ContextPackage(Event originalEvent) {
        this.eventId = originalEvent.getId();
        this.originalEvent = originalEvent;
        this.collectedAt = Instant.now();
    }

    public void addContext(ContextData data) {
        contexts.put(data.getProviderName(), data);
    }

    public String getEventId() { return eventId; }
    public Event getOriginalEvent() { return originalEvent; }
    public Map<String, ContextData> getContexts() { return contexts; }
    public Instant getCollectedAt() { return collectedAt; }
}
```

- [ ] **Step 3: Verify compilation**

Run: `mvn clean compile -f pom.xml -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add context-builder/src/main/java/com/ailog/context/ContextData.java context-builder/src/main/java/com/ailog/context/ContextPackage.java
git commit -m "feat: add ContextPackage and ContextData models"
```

---

### Task 2: ContextProvider interface

**Files:**
- Create: `context-builder/src/main/java/com/ailog/context/ContextProvider.java`

- [ ] **Step 1: Create ContextProvider.java**

```java
package com.ailog.context;

import com.ailog.common.event.Event;

public interface ContextProvider {

    String name();

    ContextData collect(Event event);
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn clean compile -f pom.xml -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add context-builder/src/main/java/com/ailog/context/ContextProvider.java
git commit -m "feat: add ContextProvider interface"
```

---

### Task 3: DockerContextProvider

**Files:**
- Create: `context-builder/src/main/java/com/ailog/context/DockerContextProvider.java`
- Test: `context-builder/src/test/java/com/ailog/context/DockerContextProviderTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.ailog.context;

import com.ailog.common.event.Event;
import com.ailog.common.event.EventType;
import com.ailog.common.event.Severity;
import com.ailog.mcp.DockerTool;
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
class DockerContextProviderTest {

    @Mock
    private DockerTool dockerTool;

    @Test
    void shouldCollectDockerContext() {
        DockerContextProvider provider = new DockerContextProvider(dockerTool);

        when(dockerTool.ps()).thenReturn("abc123  my-app  running");
        when(dockerTool.logs("abc123", 50)).thenReturn("log line 1\nlog line 2");

        Event event = new Event(UUID.randomUUID().toString(), EventType.DOCKER_CRASH, "docker", "ContainerCrash",
                "Container abc123 state: exited", Instant.now(), "docker", Severity.HIGH, Map.of("containerId", "abc123"));

        ContextData result = provider.collect(event);

        assertTrue(result.isSuccess());
        assertEquals("docker", result.getProviderName());
        assertTrue(result.getData().containsKey("ps"));
        assertTrue(result.getData().containsKey("logs"));
        verify(dockerTool).ps();
    }

    @Test
    void shouldReturnFailureWhenDockerToolThrows() {
        DockerContextProvider provider = new DockerContextProvider(dockerTool);
        when(dockerTool.ps()).thenThrow(new RuntimeException("Docker not available"));

        Event event = new Event(UUID.randomUUID().toString(), EventType.DOCKER_CRASH, "docker", "ContainerCrash",
                "error", Instant.now(), "docker", Severity.HIGH, Map.of());

        ContextData result = provider.collect(event);

        assertFalse(result.isSuccess());
        assertEquals("docker", result.getProviderName());
        assertNotNull(result.getErrorMessage());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl context-builder -Dtest=DockerContextProviderTest -q`
Expected: Tests FAIL — compilation error (DockerContextProvider not found)

- [ ] **Step 3: Write minimal implementation**

```java
package com.ailog.context;

import com.ailog.common.event.Event;
import com.ailog.mcp.DockerTool;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class DockerContextProvider implements ContextProvider {

    private final DockerTool dockerTool;

    public DockerContextProvider(DockerTool dockerTool) {
        this.dockerTool = dockerTool;
    }

    @Override
    public String name() {
        return "docker";
    }

    @Override
    public ContextData collect(Event event) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("ps", dockerTool.ps());

            String containerId = event.getPayload() != null
                    ? (String) event.getPayload().get("containerId") : null;
            if (containerId != null) {
                data.put("logs", dockerTool.logs(containerId, 50));
            }

            return ContextData.success(name(), data);
        } catch (Exception e) {
            return ContextData.failure(name(), e.getMessage());
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl context-builder -Dtest=DockerContextProviderTest -q`
Expected: Tests PASS

- [ ] **Step 5: Add mcp-tool-center dependency to context-builder/pom.xml**

```xml
    <dependencies>
        <dependency>
            <groupId>com.ailog</groupId>
            <artifactId>common</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.ailog</groupId>
            <artifactId>mcp-tool-center</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
```

- [ ] **Step 6: Commit**

```bash
git add context-builder/pom.xml context-builder/src/main/java/com/ailog/context/DockerContextProvider.java context-builder/src/test/java/com/ailog/context/DockerContextProviderTest.java
git commit -m "feat: add DockerContextProvider"
```

---

### Task 4: DbContextProvider

**Files:**
- Create: `context-builder/src/main/java/com/ailog/context/DbContextProvider.java`
- Test: `context-builder/src/test/java/com/ailog/context/DbContextProviderTest.java`

- [ ] **Step 1: Write the failing test**

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl context-builder -Dtest=DbContextProviderTest -q`
Expected: FAIL

- [ ] **Step 3: Write minimal implementation**

```java
package com.ailog.context;

import com.ailog.common.event.Event;
import com.ailog.mcp.MysqlTool;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class DbContextProvider implements ContextProvider {

    private final MysqlTool mysqlTool;

    public DbContextProvider(MysqlTool mysqlTool) {
        this.mysqlTool = mysqlTool;
    }

    @Override
    public String name() {
        return "db";
    }

    @Override
    public ContextData collect(Event event) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("slowSql", mysqlTool.slowSql());
            data.put("locks", mysqlTool.locks());
            return ContextData.success(name(), data);
        } catch (Exception e) {
            return ContextData.failure(name(), e.getMessage());
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl context-builder -Dtest=DbContextProviderTest -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add context-builder/src/main/java/com/ailog/context/DbContextProvider.java context-builder/src/test/java/com/ailog/context/DbContextProviderTest.java
git commit -m "feat: add DbContextProvider"
```

---

### Task 5: RedisContextProvider

**Files:**
- Create: `context-builder/src/main/java/com/ailog/context/RedisContextProvider.java`
- Test: `context-builder/src/test/java/com/ailog/context/RedisContextProviderTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.ailog.context;

import com.ailog.common.event.Event;
import com.ailog.common.event.EventType;
import com.ailog.common.event.Severity;
import com.ailog.mcp.RedisTool;
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
class RedisContextProviderTest {

    @Mock
    private RedisTool redisTool;

    @Test
    void shouldCollectRedisContext() {
        RedisContextProvider provider = new RedisContextProvider(redisTool);
        when(redisTool.memory()).thenReturn("used_memory_rss: 1048576");
        when(redisTool.clusterInfo()).thenReturn("redis_version: 7.0");

        Event event = new Event(UUID.randomUUID().toString(), EventType.REDIS_FAILURE, "redis", "MemoryAlert",
                "high memory", Instant.now(), "redis", Severity.MEDIUM, Map.of());

        ContextData result = provider.collect(event);

        assertTrue(result.isSuccess());
        assertEquals("redis", result.getProviderName());
        assertTrue(result.getData().containsKey("memory"));
        assertTrue(result.getData().containsKey("clusterInfo"));
    }

    @Test
    void shouldHandleException() {
        RedisContextProvider provider = new RedisContextProvider(redisTool);
        when(redisTool.memory()).thenThrow(new RuntimeException("Redis unavailable"));

        Event event = new Event(UUID.randomUUID().toString(), EventType.REDIS_FAILURE, "redis", "MemoryAlert",
                "error", Instant.now(), "redis", Severity.MEDIUM, Map.of());

        ContextData result = provider.collect(event);
        assertFalse(result.isSuccess());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl context-builder -Dtest=RedisContextProviderTest -q`
Expected: FAIL

- [ ] **Step 3: Write minimal implementation**

```java
package com.ailog.context;

import com.ailog.common.event.Event;
import com.ailog.mcp.RedisTool;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class RedisContextProvider implements ContextProvider {

    private final RedisTool redisTool;

    public RedisContextProvider(RedisTool redisTool) {
        this.redisTool = redisTool;
    }

    @Override
    public String name() {
        return "redis";
    }

    @Override
    public ContextData collect(Event event) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("memory", redisTool.memory());
            data.put("clusterInfo", redisTool.clusterInfo());
            return ContextData.success(name(), data);
        } catch (Exception e) {
            return ContextData.failure(name(), e.getMessage());
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl context-builder -Dtest=RedisContextProviderTest -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add context-builder/src/main/java/com/ailog/context/RedisContextProvider.java context-builder/src/test/java/com/ailog/context/RedisContextProviderTest.java
git commit -m "feat: add RedisContextProvider"
```

---

### Task 6: MqContextProvider

**Files:**
- Create: `context-builder/src/main/java/com/ailog/context/MqContextProvider.java`
- Test: `context-builder/src/test/java/com/ailog/context/MqContextProviderTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.ailog.context;

import com.ailog.common.event.Event;
import com.ailog.common.event.EventType;
import com.ailog.common.event.Severity;
import com.ailog.mcp.MqTool;
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
class MqContextProviderTest {

    @Mock
    private MqTool mqTool;

    @Test
    void shouldCollectMqContext() {
        MqContextProvider provider = new MqContextProvider(mqTool);
        when(mqTool.lag("DEFAULT_CONSUMER")).thenReturn("client  connected");
        when(mqTool.retry("DEFAULT_CONSUMER")).thenReturn("Retry queue size: 5");

        Event event = new Event(UUID.randomUUID().toString(), EventType.MQ_LAG, "rocketmq", "ConsumerLag",
                "lag detected", Instant.now(), "rocketmq", Severity.MEDIUM, Map.of());

        ContextData result = provider.collect(event);

        assertTrue(result.isSuccess());
        assertEquals("mq", result.getProviderName());
        assertTrue(result.getData().containsKey("lag"));
        assertTrue(result.getData().containsKey("retry"));
    }

    @Test
    void shouldHandleException() {
        MqContextProvider provider = new MqContextProvider(mqTool);
        when(mqTool.lag(anyString())).thenThrow(new RuntimeException("MQ unavailable"));

        Event event = new Event(UUID.randomUUID().toString(), EventType.MQ_LAG, "rocketmq", "ConsumerLag",
                "error", Instant.now(), "rocketmq", Severity.MEDIUM, Map.of());

        ContextData result = provider.collect(event);
        assertFalse(result.isSuccess());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl context-builder -Dtest=MqContextProviderTest -q`
Expected: FAIL

- [ ] **Step 3: Write minimal implementation**

```java
package com.ailog.context;

import com.ailog.common.event.Event;
import com.ailog.mcp.MqTool;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class MqContextProvider implements ContextProvider {

    private final MqTool mqTool;

    public MqContextProvider(MqTool mqTool) {
        this.mqTool = mqTool;
    }

    @Override
    public String name() {
        return "mq";
    }

    @Override
    public ContextData collect(Event event) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("lag", mqTool.lag("DEFAULT_CONSUMER"));
            data.put("retry", mqTool.retry("DEFAULT_CONSUMER"));
            return ContextData.success(name(), data);
        } catch (Exception e) {
            return ContextData.failure(name(), e.getMessage());
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl context-builder -Dtest=MqContextProviderTest -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add context-builder/src/main/java/com/ailog/context/MqContextProvider.java context-builder/src/test/java/com/ailog/context/MqContextProviderTest.java
git commit -m "feat: add MqContextProvider"
```

---

### Task 7: GitContextProvider

Calls `git log` via `Runtime.exec()` to get recent commits for the service identified by `event.getServiceName()`.

**Files:**
- Create: `context-builder/src/main/java/com/ailog/context/GitContextProvider.java`
- Test: `context-builder/src/test/java/com/ailog/context/GitContextProviderTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.ailog.context;

import com.ailog.common.event.Event;
import com.ailog.common.event.EventType;
import com.ailog.common.event.Severity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GitContextProviderTest {

    private final GitContextProvider provider = new GitContextProvider();

    @Test
    void shouldReturnSuccessEvenIfGitFails() {
        Event event = new Event(UUID.randomUUID().toString(), EventType.LOG_ERROR, "order-service", "LogError",
                "NullPointerException", Instant.now(), "/var/log/order-service.log", Severity.HIGH, Map.of());

        ContextData result = provider.collect(event);

        // Git may not be available in test environment, but should not throw
        assertNotNull(result);
        assertEquals("git", result.getProviderName());
    }

    @Test
    void shouldHandleNullServiceName() {
        Event event = new Event(UUID.randomUUID().toString(), EventType.LOG_ERROR, null, "LogError",
                "error", Instant.now(), "unknown", Severity.HIGH, Map.of());

        ContextData result = provider.collect(event);

        assertNotNull(result);
        assertTrue(result.isSuccess() || !result.isSuccess());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl context-builder -Dtest=GitContextProviderTest -q`
Expected: FAIL

- [ ] **Step 3: Write minimal implementation**

```java
package com.ailog.context;

import com.ailog.common.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class GitContextProvider implements ContextProvider {

    private static final Logger log = LoggerFactory.getLogger(GitContextProvider.class);

    @Override
    public String name() {
        return "git";
    }

    @Override
    public ContextData collect(Event event) {
        try {
            Map<String, Object> data = new HashMap<>();
            String serviceName = event.getServiceName();
            if (serviceName != null && !serviceName.isBlank()) {
                data.put("recentCommits", exec("git log --oneline -10 -- " + serviceName));
                data.put("recentChanges", exec("git diff --name-only HEAD~5 HEAD"));
            }
            return ContextData.success(name(), data);
        } catch (Exception e) {
            log.warn("Git context collection failed: {}", e.getMessage());
            return ContextData.failure(name(), e.getMessage());
        }
    }

    private String exec(String cmd) {
        try {
            Process process = Runtime.getRuntime().exec(cmd);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl context-builder -Dtest=GitContextProviderTest -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add context-builder/src/main/java/com/ailog/context/GitContextProvider.java context-builder/src/test/java/com/ailog/context/GitContextProviderTest.java
git commit -m "feat: add GitContextProvider"
```

---

### Task 8: CodeContextProvider

Reads Java source files based on the event's exception type or error message. Tries to locate matching files under a configured source path.

**Files:**
- Create: `context-builder/src/main/java/com/ailog/context/CodeContextProvider.java`
- Test: `context-builder/src/test/java/com/ailog/context/CodeContextProviderTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.ailog.context;

import com.ailog.common.event.Event;
import com.ailog.common.event.EventType;
import com.ailog.common.event.Severity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CodeContextProviderTest {

    private final CodeContextProvider provider = new CodeContextProvider("src/main/java");

    @Test
    void shouldHandleEmptyErrorMessage() {
        Event event = new Event(UUID.randomUUID().toString(), EventType.LOG_ERROR, "order-service", "NullPointerException",
                null, Instant.now(), "source", Severity.HIGH, Map.of());

        ContextData result = provider.collect(event);

        assertNotNull(result);
        assertEquals("code", result.getProviderName());
    }

    @Test
    void shouldNotThrowOnAnyInput() {
        Event event = new Event(UUID.randomUUID().toString(), EventType.LOG_ERROR, "order-service", "NullPointerException",
                "at com.ailog.order.OrderService.createOrder(OrderService.java:42)",
                Instant.now(), "source", Severity.HIGH, Map.of());

        ContextData result = provider.collect(event);

        assertNotNull(result);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl context-builder -Dtest=CodeContextProviderTest -q`
Expected: FAIL

- [ ] **Step 3: Write minimal implementation**

```java
package com.ailog.context;

import com.ailog.common.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
public class CodeContextProvider implements ContextProvider {

    private static final Logger log = LoggerFactory.getLogger(CodeContextProvider.class);
    private static final Pattern CLASS_PATTERN = Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*)\\.java");

    private final String sourcePath;

    public CodeContextProvider(@Value("${context.code.source-path:src/main/java}") String sourcePath) {
        this.sourcePath = sourcePath;
    }

    @Override
    public String name() {
        return "code";
    }

    @Override
    public ContextData collect(Event event) {
        try {
            Map<String, Object> data = new HashMap<>();
            String errorMessage = event.getErrorMessage();
            if (errorMessage != null && !errorMessage.isBlank()) {
                String className = extractClassName(errorMessage);
                if (className != null) {
                    data.put("relatedSources", findSourceFiles(className));
                }
            }
            return ContextData.success(name(), data);
        } catch (Exception e) {
            return ContextData.failure(name(), e.getMessage());
        }
    }

    private String extractClassName(String text) {
        Matcher m = CLASS_PATTERN.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private String findSourceFiles(String className) {
        Path base = Paths.get(sourcePath);
        if (!Files.exists(base)) return "Source path not found: " + sourcePath;

        try (Stream<Path> walk = Files.walk(base)) {
            return walk.filter(p -> p.getFileName().toString().equals(className + ".java"))
                    .limit(3)
                    .map(p -> {
                        try {
                            return "--- " + p + " ---\n" + Files.readString(p);
                        } catch (IOException e) {
                            return "Error reading " + p + ": " + e.getMessage();
                        }
                    })
                    .collect(java.util.stream.Collectors.joining("\n\n"));
        } catch (IOException e) {
            return "Error scanning sources: " + e.getMessage();
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl context-builder -Dtest=CodeContextProviderTest -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add context-builder/src/main/java/com/ailog/context/CodeContextProvider.java context-builder/src/test/java/com/ailog/context/CodeContextProviderTest.java
git commit -m "feat: add CodeContextProvider"
```

---

### Task 9: EnvContextProvider

Collects JVM and OS environment info. No external dependencies.

**Files:**
- Create: `context-builder/src/main/java/com/ailog/context/EnvContextProvider.java`
- Test: `context-builder/src/test/java/com/ailog/context/EnvContextProviderTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.ailog.context;

import com.ailog.common.event.Event;
import com.ailog.common.event.EventType;
import com.ailog.common.event.Severity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EnvContextProviderTest {

    private final EnvContextProvider provider = new EnvContextProvider();

    @Test
    void shouldCollectEnvContext() {
        Event event = new Event(UUID.randomUUID().toString(), EventType.LOG_ERROR, "order-service", "LogError",
                "error", Instant.now(), "source", Severity.HIGH, Map.of());

        ContextData result = provider.collect(event);

        assertTrue(result.isSuccess());
        assertEquals("env", result.getProviderName());
        assertTrue(result.getData().containsKey("javaVersion"));
        assertTrue(result.getData().containsKey("osName"));
        assertTrue(result.getData().containsKey("availableProcessors"));
    }

    @Test
    void shouldWorkWithAnyEvent() {
        ContextData result = provider.collect(null);
        assertTrue(result.isSuccess());
        assertTrue(result.getData().containsKey("javaVersion"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl context-builder -Dtest=EnvContextProviderTest -q`
Expected: FAIL

- [ ] **Step 3: Write minimal implementation**

```java
package com.ailog.context;

import com.ailog.common.event.Event;
import org.springframework.stereotype.Component;

import java.lang.Runtime;
import java.util.HashMap;
import java.util.Map;

@Component
public class EnvContextProvider implements ContextProvider {

    @Override
    public String name() {
        return "env";
    }

    @Override
    public ContextData collect(Event event) {
        Map<String, Object> data = new HashMap<>();
        data.put("javaVersion", System.getProperty("java.version", "unknown"));
        data.put("osName", System.getProperty("os.name", "unknown"));
        data.put("osArch", System.getProperty("os.arch", "unknown"));
        data.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        data.put("maxMemory", Runtime.getRuntime().maxMemory());
        return ContextData.success(name(), data);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl context-builder -Dtest=EnvContextProviderTest -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add context-builder/src/main/java/com/ailog/context/EnvContextProvider.java context-builder/src/test/java/com/ailog/context/EnvContextProviderTest.java
git commit -m "feat: add EnvContextProvider"
```

---

### Task 10: ContextBuilderService — orchestrator

Wires all ContextProvider beans together, subscribes to EventBus, and produces ContextPackage on each event.

**Files:**
- Create: `context-builder/src/main/java/com/ailog/context/ContextBuilderService.java`
- Test: `context-builder/src/test/java/com/ailog/context/ContextBuilderServiceTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.ailog.context;

import com.ailog.common.event.Event;
import com.ailog.common.event.EventType;
import com.ailog.common.event.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContextBuilderServiceTest {

    @Mock
    private ContextProvider mockProvider1;

    @Mock
    private ContextProvider mockProvider2;

    private ContextBuilderService service;

    @BeforeEach
    void setUp() {
        when(mockProvider1.name()).thenReturn("provider1");
        when(mockProvider2.name()).thenReturn("provider2");
        service = new ContextBuilderService(List.of(mockProvider1, mockProvider2));
    }

    @Test
    void shouldBuildContextPackageFromAllProviders() {
        Event event = new Event(UUID.randomUUID().toString(), EventType.LOG_ERROR, "order-service", "LogError",
                "error msg", Instant.now(), "source", Severity.HIGH, Map.of());

        when(mockProvider1.collect(event)).thenReturn(ContextData.success("provider1", Map.of("key", "val1")));
        when(mockProvider2.collect(event)).thenReturn(ContextData.success("provider2", Map.of("key", "val2")));

        ContextPackage result = service.build(event);

        assertNotNull(result);
        assertEquals(event.getId(), result.getEventId());
        assertSame(event, result.getOriginalEvent());
        assertEquals(2, result.getContexts().size());
    }

    @Test
    void shouldStillBuildWhenSomeProvidersFail() {
        Event event = new Event(UUID.randomUUID().toString(), EventType.LOG_ERROR, "order-service", "LogError",
                "error msg", Instant.now(), "source", Severity.HIGH, Map.of());

        when(mockProvider1.collect(event)).thenReturn(ContextData.failure("provider1", "error"));
        when(mockProvider2.collect(event)).thenReturn(ContextData.success("provider2", Map.of("key", "val")));

        ContextPackage result = service.build(event);

        assertNotNull(result);
        assertEquals(2, result.getContexts().size());
        assertFalse(result.getContexts().get("provider1").isSuccess());
        assertTrue(result.getContexts().get("provider2").isSuccess());
    }

    @Test
    void shouldHandleEmptyProviderList() {
        ContextBuilderService emptyService = new ContextBuilderService(List.of());
        Event event = new Event(UUID.randomUUID().toString(), EventType.LOG_ERROR, "s", "e",
                "m", Instant.now(), "src", Severity.LOW, Map.of());

        ContextPackage result = emptyService.build(event);
        assertNotNull(result);
        assertTrue(result.getContexts().isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl context-builder -Dtest=ContextBuilderServiceTest -q`
Expected: FAIL

- [ ] **Step 3: Write minimal implementation**

```java
package com.ailog.context;

import com.ailog.common.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ContextBuilderService {

    private static final Logger log = LoggerFactory.getLogger(ContextBuilderService.class);

    private final List<ContextProvider> providers;

    public ContextBuilderService(List<ContextProvider> providers) {
        this.providers = providers;
    }

    public ContextPackage build(Event event) {
        ContextPackage pkg = new ContextPackage(event);
        for (ContextProvider provider : providers) {
            try {
                ContextData data = provider.collect(event);
                pkg.addContext(data);
                log.debug("Provider {} completed for event {}", provider.name(), event.getId());
            } catch (Exception e) {
                log.error("Provider {} failed for event {}: {}", provider.name(), event.getId(), e.getMessage());
                pkg.addContext(ContextData.failure(provider.name(), e.getMessage()));
            }
        }
        return pkg;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl context-builder -Dtest=ContextBuilderServiceTest -q`
Expected: PASS

- [ ] **Step 5: Repository commit**

Run `git init` in the project root if git hasn't been initialized yet. Otherwise:

```bash
git add context-builder/src/main/java/com/ailog/context/ContextBuilderService.java context-builder/src/test/java/com/ailog/context/ContextBuilderServiceTest.java
git commit -m "feat: add ContextBuilderService orchestrator"
```

---

## Self-Review

**Spec coverage check:**
- 7 providers implemented: DockerContextProvider (Task 3), DbContextProvider (Task 4), RedisContextProvider (Task 5), MqContextProvider (Task 6), GitContextProvider (Task 7), CodeContextProvider (Task 8), EnvContextProvider (Task 9) — all match spec
- ContextBuilderService orchestrates all providers, each independent, fail isolated (Task 10) — matches spec
- ContextPackage + ContextData models (Task 1) — matches spec
- ContextProvider interface (Task 2) — matches spec with `name()` and `collect(Event)` signature
- EventBus subscription not included in this phase (ContextBuilderService is called externally; subscription can be added as a listener adapter later) — aligns with "non-blocking first iteration"

**Placeholder scan:** OK — all steps contain complete code

**Type consistency:** `ContextData.success/failure` static factory methods referenced consistently across all providers. `ContextPackage.addContext()` called with `ContextData` in all tasks. All method signatures match.
