# Root Cause Engine — 改进实现计划

**Goal:** 解决 root-cause-engine 四个待办项：类型安全、EventBus 自动触发、多事件关联、规则可配置。

**Architecture:** 新增 EventCorrelationEngine 做三层关联 + RootCauseEventListener 接入 EventBus + ContextData 加类型安全访问器 + 规则条件抽到 application.yml。

**Tech Stack:** Spring Boot 3.4.4, Java 21, Easy Rules 4.1.0, JUnit 5 + Mockito

---

## Task 1: ContextData — Key 常量 + 类型安全访问器

**Files:**
- Modify: `common/src/main/java/com/ailog/common/event/ContextData.java`

- [ ] **Step 1: 修改 ContextData.java**

```java
package com.ailog.context;

import java.util.Map;

public class ContextData {

    public static final String GIT_COMMITS = "recentCommits";
    public static final String GIT_CHANGES = "recentChanges";
    public static final String DOCKER_PS = "ps";
    public static final String DOCKER_LOGS = "logs";
    public static final String DB_SLOW_SQL = "slowSql";
    public static final String DB_LOCKS = "locks";
    public static final String REDIS_MEMORY = "memory";
    public static final String REDIS_CLUSTER = "clusterInfo";
    public static final String MQ_LAG = "lag";
    public static final String MQ_RETRY = "retry";
    public static final String ENV_JAVA_VERSION = "javaVersion";

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

    public <T> T get(String key, Class<T> type) {
        Object value = data.get(key);
        if (value == null) return null;
        return type.cast(value);
    }

    public String getProviderName() { return providerName; }
    public Map<String, Object> getData() { return data; }
    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }
}
```

---

## Task 2: 6 个 Provider 改用 Key 常量

**Files:**
- Modify: 6 files in context-builder

- [ ] **Step 1: DockerContextProvider** — `ContextData.DOCKER_LOGS` 替换 `"logs"`

```java
data.put(ContextData.DOCKER_LOGS, dockerTool.logs(containerId, 50));
```

- [ ] **Step 2: DbContextProvider** — `ContextData.DB_SLOW_SQL`, `ContextData.DB_LOCKS` 替换硬编码 key

- [ ] **Step 3: RedisContextProvider** — `ContextData.REDIS_MEMORY`, `ContextData.REDIS_CLUSTER`

- [ ] **Step 4: MqContextProvider** — `ContextData.MQ_LAG`, `ContextData.MQ_RETRY`

- [ ] **Step 5: GitContextProvider** — `ContextData.GIT_COMMITS`, `ContextData.GIT_CHANGES`

- [ ] **Step 6: EnvContextProvider** — `ContextData.ENV_JAVA_VERSION`

---

## Task 3: EventGroup 模型

**Files:**
- Create: `root-cause-engine/src/main/java/com/ailog/rootcause/EventGroup.java`

```java
package com.ailog.rootcause;

import com.ailog.context.ContextData;
import com.ailog.context.ContextPackage;
import com.ailog.common.event.Event;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class EventGroup {

    private static final Duration WINDOW = Duration.ofMinutes(5);

    private final String groupKey;
    private final String matchType;
    private final List<ContextPackage> events = new ArrayList<>();
    private final Instant windowStart;
    private final Instant windowEnd;

    public EventGroup(String groupKey, String matchType, ContextPackage first) {
        this.groupKey = groupKey;
        this.matchType = matchType;
        this.events.add(first);
        this.windowStart = first.getCollectedAt();
        this.windowEnd = windowStart.plus(WINDOW);
    }

    public void add(ContextPackage pkg) {
        events.add(pkg);
    }

    public boolean isExpired() {
        return Instant.now().isAfter(windowEnd);
    }

    public ContextPackage merge() {
        Event first = events.get(0).getOriginalEvent();
        ContextPackage merged = new ContextPackage(new Event(
                first.getId(), first.getType(), first.getServiceName(),
                first.getExceptionType(), first.getErrorMessage(),
                windowStart, first.getSource(), first.getSeverity(),
                first.getPayload()
        ));
        for (ContextPackage pkg : events) {
            for (ContextData cd : pkg.getContexts().values()) {
                if (merged.getContexts().containsKey(cd.getProviderName())) {
                    continue;
                }
                merged.addContext(cd);
            }
        }
        return merged;
    }

    public int size() { return events.size(); }
    public String getGroupKey() { return groupKey; }
    public String getMatchType() { return matchType; }
    public List<ContextPackage> getEvents() { return events; }
}
```

---

## Task 4: EventCorrelationEngine

**Files:**
- Create: `root-cause-engine/src/main/java/com/ailog/rootcause/EventCorrelationEngine.java`

```java
package com.ailog.rootcause;

import com.ailog.context.ContextPackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EventCorrelationEngine {

    private static final Logger log = LoggerFactory.getLogger(EventCorrelationEngine.class);

    private final Map<String, EventGroup> pendingGroups = new ConcurrentHashMap<>();
    private final RootCauseEngineService analyzer;

    public EventCorrelationEngine(RootCauseEngineService analyzer) {
        this.analyzer = analyzer;
    }

    public void feed(ContextPackage pkg) {
        var event = pkg.getOriginalEvent();

        String fingerprint = buildFingerprint(event);
        EventGroup group = pendingGroups.get(fingerprint);
        if (group != null) {
            group.add(pkg);
            if (group.isExpired()) flush(group, pendingGroups.remove(fingerprint));
            return;
        }

        String serviceKey = event.getServiceName() + ":" + event.getSource();
        group = pendingGroups.get(serviceKey);
        if (group != null) {
            group.add(pkg);
            if (group.isExpired()) flush(group, pendingGroups.remove(serviceKey));
            return;
        }

        EventGroup newGroup = new EventGroup(fingerprint != null ? fingerprint : serviceKey,
                fingerprint != null ? "FINGERPRINT" : "SERVICE_WINDOW", pkg);
        pendingGroups.put(fingerprint != null ? fingerprint : serviceKey, newGroup);
    }

    @Scheduled(fixedDelay = 60000)
    public void scanExpired() {
        var iter = pendingGroups.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            if (entry.getValue().isExpired()) {
                flush(entry.getValue(), entry.getValue());
                iter.remove();
            }
        }
    }

    private void flush(EventGroup group, EventGroup removed) {
        if (group.size() == 1 && "FINGERPRINT".equals(group.getMatchType())) {
            analyzer.analyze(group);
            return;
        }
        applyCrossServiceRules(group);
        analyzer.analyze(group);
    }

    private void applyCrossServiceRules(EventGroup group) {
        boolean hasMqLag = false, hasDbSlow = false;
        for (var pkg : group.getEvents()) {
            switch (pkg.getOriginalEvent().getType()) {
                case MQ_LAG -> hasMqLag = true;
                case DB_SLOW_QUERY -> hasDbSlow = true;
            }
        }
        if (hasMqLag && hasDbSlow) {
            log.info("Cross-service rule matched: MQ_LAG + DB_SLOW_QUERY → DB_PERFORMANCE");
        }
    }

    private String buildFingerprint(com.ailog.common.event.Event event) {
        String type = event.getExceptionType();
        String msg = event.getErrorMessage();
        if (type == null && msg == null) return null;
        String raw = (type != null ? type : "") + ":" + (msg != null ? msg.substring(0, Math.min(msg.length(), 50)) : "");
        return Integer.toHexString(raw.hashCode());
    }
}
```

---

## Task 5: RootCauseEngineService 改为接收 EventGroup

- [ ] **Step 1: 修改 RootCauseEngineService**

```java
public RootCauseReport analyze(EventGroup group) {
    ContextPackage context = group.merge();  // 多事件合并
    // 后续逻辑不变
}
```

- [ ] **Step 2: 更新 RootCauseEngineServiceTest**

测试覆盖：
- 单事件分析（直接传入 EventGroup）
- 多事件合并后分析

---

## Task 6: RootCauseEventListener

**Files:**
- Create: `root-cause-engine/src/main/java/com/ailog/rootcause/RootCauseEventListener.java`

```java
package com.ailog.rootcause;

import com.ailog.common.event.Event;
import com.ailog.common.event.EventBus;
import com.ailog.common.event.EventType;
import com.ailog.context.ContextBuilderService;
import com.ailog.context.ContextPackage;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RootCauseEventListener {

    private static final Logger log = LoggerFactory.getLogger(RootCauseEventListener.class);

    private final EventBus eventBus;
    private final ContextBuilderService contextBuilder;
    private final EventCorrelationEngine correlator;

    public RootCauseEventListener(EventBus eventBus, ContextBuilderService contextBuilder,
                                  EventCorrelationEngine correlator) {
        this.eventBus = eventBus;
        this.contextBuilder = contextBuilder;
        this.correlator = correlator;
    }

    @PostConstruct
    public void subscribe() {
        EventType[] types = {
                EventType.LOG_ERROR, EventType.DB_SLOW_QUERY,
                EventType.DOCKER_CRASH, EventType.REDIS_FAILURE, EventType.MQ_LAG
        };
        for (EventType type : types) {
            eventBus.subscribe(type, this::onEvent);
        }
        log.info("RootCauseEventListener subscribed to {} event types", types.length);
    }

    private void onEvent(Event event) {
        try {
            ContextPackage pkg = contextBuilder.build(event);
            correlator.feed(pkg);
        } catch (Exception e) {
            log.error("Failed to process event {}: {}", event.getId(), e.getMessage());
        }
    }
}
```

---

## Task 7: Rules 改造 — 使用 ContextData.get(Class) + 配置注入

- [ ] **Step 1: CodeDefectRule**

```java
// 之前
String commits = (String) gitData.getData().get("recentCommits");

// 之后
String commits = gitData.get(ContextData.GIT_COMMITS, String.class);
```

- [ ] **Step 2: ResourceExhaustionRule** — 同上 + `@Value` 注入 docker-oom-keywords

```java
public ResourceExhaustionRule(
        @Value("${rootcause.rules.resource-exhaustion.docker-oom-keywords}")
        List<String> oomKeywords) {
    this.oomKeywords = oomKeywords;
}
```

- [ ] **Step 3: ConfigErrorRule** — 同上 + `@Value` 注入 keywords

```java
public ConfigErrorRule(
        @Value("${rootcause.rules.config-error.keywords}")
        List<String> keywords) {
    this.configKeywords = keywords;
}
```

- [ ] **Step 4: DbPerformanceRule** — 仅改用 `ContextData.get()`

- [ ] **Step 5: DependencyFailureRule** — 仅改用 `ContextData.get()`

---

## Task 8: application.yml — 添加规则配置

**Files:**
- Modify: `orchestrator/src/main/resources/application.yml`

```yaml
rootcause:
  rules:
    config-error:
      keywords:
        - config
        - FileNotFound
        - NoSuchFile
        - application.yml
        - application.properties
        - not found
        - InvalidConfig
    resource-exhaustion:
      docker-oom-keywords:
        - OOM
        - oom
        - kill
```
