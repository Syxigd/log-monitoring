# Observe Engine 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 实现 observe-engine 模块，支持 5 种事件源的轮询采集并发布到 EventBus

**Architecture:** EventCollector 接口 + 5 个 Collector 实现 + ObserveScheduler 定时调度。Docker/MySQL/Redis/MQ 四个 Collector 复用 mcp-tool-center 的 Service Bean。LogCollector 使用 Java WatchService 独立实现。

**Tech Stack:** Java 21 + Spring Boot 3.4.4 + Spring Scheduling

---

### Task 1: EventCollector 接口 + ObserveScheduler 骨架

**Files:**
- Create: `observe-engine/src/main/java/com/ailog/observe/collector/EventCollector.java`
- Create: `observe-engine/src/main/java/com/ailog/observe/scheduler/ObserveScheduler.java`
- Create: `observe-engine/src/main/java/com/ailog/observe/ObserveEngineService.java`

- [ ] **Step 1: Create EventCollector.java**

```java
package com.ailog.observe.collector;

import com.ailog.common.event.Event;

import java.util.List;

public interface EventCollector {

    String name();

    List<Event> collect();
}
```

- [ ] **Step 2: Create ObserveEngineService.java**

```java
package com.ailog.observe;

import com.ailog.common.event.Event;
import com.ailog.common.event.EventBus;
import com.ailog.observe.collector.EventCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ObserveEngineService {

    private static final Logger log = LoggerFactory.getLogger(ObserveEngineService.class);

    private final List<EventCollector> collectors;
    private final EventBus eventBus;

    public ObserveEngineService(List<EventCollector> collectors, EventBus eventBus) {
        this.collectors = collectors;
        this.eventBus = eventBus;
    }

    public void collectAll() {
        for (EventCollector collector : collectors) {
            try {
                List<Event> events = collector.collect();
                for (Event event : events) {
                    eventBus.publish(event);
                }
            } catch (Exception e) {
                log.error("Collector {} failed: {}", collector.name(), e.getMessage());
            }
        }
    }
}
```

- [ ] **Step 3: Create ObserveScheduler.java**

```java
package com.ailog.observe.scheduler;

import com.ailog.observe.ObserveEngineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class ObserveScheduler {

    private static final Logger log = LoggerFactory.getLogger(ObserveScheduler.class);

    private final ObserveEngineService observeEngineService;

    public ObserveScheduler(ObserveEngineService observeEngineService) {
        this.observeEngineService = observeEngineService;
    }

    @Scheduled(fixedDelayString = "${observe.collector.interval-ms:30000}")
    public void runCollectors() {
        log.debug("Running all collectors");
        observeEngineService.collectAll();
    }
}
```

- [ ] **Step 4: Update observe-engine/application.yml**

```yaml
observe-engine:
  enabled: true

observe:
  collector:
    interval-ms: 30000
```

---

### Task 2: LogCollector（文件日志监听）

**Files:**
- Create: `observe-engine/src/main/java/com/ailog/observe/collector/LogCollector.java`

- [ ] **Step 1: Create LogCollector.java**

```java
package com.ailog.observe.collector;

import com.ailog.common.event.Event;
import com.ailog.common.event.EventType;
import com.ailog.common.event.Severity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Component
public class LogCollector implements EventCollector {

    private static final Logger log = LoggerFactory.getLogger(LogCollector.class);
    private static final Pattern ERROR_PATTERN = Pattern.compile("\\bERROR\\b", Pattern.CASE_INSENSITIVE);

    private final String logPath;
    private final Map<String, Long> filePositions = new ConcurrentHashMap<>();

    public LogCollector(@Value("${observe.collector.log.path:}") String logPath) {
        this.logPath = logPath;
    }

    @Override
    public String name() {
        return "log";
    }

    @Override
    public List<Event> collect() {
        if (logPath == null || logPath.isBlank()) {
            return List.of();
        }

        List<Event> events = new ArrayList<>();
        Path dir = Paths.get(logPath);

        if (!Files.exists(dir)) {
            return List.of();
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.log")) {
            for (Path file : stream) {
                events.addAll(scanFile(file));
            }
        } catch (IOException e) {
            log.error("Failed to scan log directory: {}", e.getMessage());
        }

        return events;
    }

    private List<Event> scanFile(Path file) {
        List<Event> events = new ArrayList<>();
        String key = file.toAbsolutePath().toString();
        long lastPos = filePositions.getOrDefault(key, 0L);

        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            long len = raf.length();
            if (len < lastPos) {
                lastPos = 0;
            }
            raf.seek(lastPos);

            String line;
            while ((line = raf.readLine()) != null) {
                if (ERROR_PATTERN.matcher(line).find()) {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("file", key);
                    payload.put("line", line);
                    events.add(new Event(
                            UUID.randomUUID().toString(),
                            EventType.LOG_ERROR,
                            Instant.now(),
                            key,
                            Severity.HIGH,
                            payload
                    ));
                }
            }
            filePositions.put(key, raf.getFilePointer());
        } catch (IOException e) {
            log.error("Failed to read log file {}: {}", key, e.getMessage());
        }

        return events;
    }
}
```

---

### Task 3: DockerCollector

**Files:**
- Create: `observe-engine/src/main/java/com/ailog/observe/collector/DockerCollector.java`

- [ ] **Step 1: Create DockerCollector.java**

```java
package com.ailog.observe.collector;

import com.ailog.common.event.Event;
import com.ailog.common.event.EventType;
import com.ailog.common.event.Severity;
import com.ailog.mcp.DockerTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
public class DockerCollector implements EventCollector {

    private static final Logger log = LoggerFactory.getLogger(DockerCollector.class);

    private final DockerTool dockerTool;
    private final Map<String, String> previousStates = new HashMap<>();

    public DockerCollector(DockerTool dockerTool) {
        this.dockerTool = dockerTool;
    }

    @Override
    public String name() {
        return "docker";
    }

    @Override
    public List<Event> collect() {
        List<Event> events = new ArrayList<>();
        try {
            String ps = dockerTool.ps();
            if (ps == null || ps.isBlank()) return events;

            String[] lines = ps.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.isBlank()) continue;
                String[] parts = line.split("\\s+", 3);
                if (parts.length < 3) continue;

                String containerId = parts[0];
                String state = parts[2];
                String previous = previousStates.get(containerId);

                if ("exited".equalsIgnoreCase(state) || "unhealthy".equalsIgnoreCase(state)) {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("containerId", containerId);
                    payload.put("state", state);
                    payload.put("line", line);
                    events.add(new Event(
                            UUID.randomUUID().toString(),
                            EventType.DOCKER_CRASH,
                            Instant.now(),
                            "docker",
                            Severity.HIGH,
                            payload
                    ));
                }
                previousStates.put(containerId, state);
            }
        } catch (Exception e) {
            log.error("Docker collector failed: {}", e.getMessage());
        }
        return events;
    }
}
```

---

### Task 4: MysqlCollector

**Files:**
- Create: `observe-engine/src/main/java/com/ailog/observe/collector/MysqlCollector.java`

- [ ] **Step 1: Create MysqlCollector.java**

```java
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
```

---

### Task 5: RedisCollector

**Files:**
- Create: `observe-engine/src/main/java/com/ailog/observe/collector/RedisCollector.java`

- [ ] **Step 1: Create RedisCollector.java**

```java
package com.ailog.observe.collector;

import com.ailog.common.event.Event;
import com.ailog.common.event.EventType;
import com.ailog.common.event.Severity;
import com.ailog.mcp.RedisTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
public class RedisCollector implements EventCollector {

    private static final Logger log = LoggerFactory.getLogger(RedisCollector.class);

    private final RedisTool redisTool;

    public RedisCollector(RedisTool redisTool) {
        this.redisTool = redisTool;
    }

    @Override
    public String name() {
        return "redis";
    }

    @Override
    public List<Event> collect() {
        List<Event> events = new ArrayList<>();

        try {
            String mem = redisTool.memory();
            if (mem != null && mem.contains("used_memory_rss")) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("details", mem);
                events.add(new Event(
                        UUID.randomUUID().toString(),
                        EventType.REDIS_FAILURE,
                        Instant.now(),
                        "redis",
                        Severity.MEDIUM,
                        payload
                ));
            }
        } catch (Exception e) {
            log.error("Redis collector failed: {}", e.getMessage());
        }

        return events;
    }
}
```

---

### Task 6: MqCollector

**Files:**
- Create: `observe-engine/src/main/java/com/ailog/observe/collector/MqCollector.java`

- [ ] **Step 1: Create MqCollector.java**

```java
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
```

---

### Task 7: 编译验证

- [ ] **Step 1: 全量编译**

```bash
export JAVA_HOME=/c/Users/sunyuxin/.jdks/openjdk-21.0.2
export MAVEN_HOME=/tmp/apache-maven-3.9.16
export PATH=$MAVEN_HOME/bin:$JAVA_HOME/bin:$PATH
mvn clean compile -f "D:/Claude-Create-Project/ai-log-monitoring/pom.xml"
```

Expected: BUILD SUCCESS
