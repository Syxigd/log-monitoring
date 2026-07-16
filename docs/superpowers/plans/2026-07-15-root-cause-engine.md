# Root Cause Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build root-cause-engine that receives ContextPackage, runs deterministic evidence-chain rules via Easy Rules, and outputs a RootCauseReport.

**Architecture:** Two-layer rule chain — first layer checks direct causes (code defect / resource exhaustion / config error), second layer checks symptoms (db performance / dependency failure). All rules use Easy Rules 4.x. RootCauseEngineService orchestrates priority-ordered execution and selects the best match by evidence chain length.

**Tech Stack:** Spring Boot 3.4.4, Java 21, Easy Rules 4.1.0, JUnit 5 + Mockito

---

## File Structure

```
root-cause-engine/pom.xml                                   ← MODIFY: add dependencies

root-cause-engine/src/main/java/com/ailog/rootcause/
  ├── RootCause.java                                        ← NEW: enum
  ├── RootCauseReport.java                                  ← NEW: output model
  ├── RootCauseAnalyzer.java                                ← NEW: rule interface wrapping Easy Rule
  ├── RootCauseEngineService.java                           ← NEW: orchestrator
  └── rule/
      ├── CodeDefectRule.java                               ← NEW: layer 1 rule
      ├── ResourceExhaustionRule.java                       ← NEW: layer 1 rule
      ├── ConfigErrorRule.java                              ← NEW: layer 1 rule
      ├── DbPerformanceRule.java                            ← NEW: layer 2 rule
      └── DependencyFailureRule.java                        ← NEW: layer 2 rule

root-cause-engine/src/test/java/com/ailog/rootcause/
  ├── RootCauseEngineServiceTest.java                       ← NEW
  ├── CodeDefectRuleTest.java                               ← NEW
  ├── ResourceExhaustionRuleTest.java                       ← NEW
  ├── ConfigErrorRuleTest.java                              ← NEW
  ├── DbPerformanceRuleTest.java                            ← NEW
  └── DependencyFailureRuleTest.java                        ← NEW
```

---

### Task 1: Update pom.xml — easy-rules + context-builder

**Files:**
- Modify: `root-cause-engine/pom.xml`

- [ ] **Step 1: Update pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.ailog</groupId>
        <artifactId>ai-log-monitoring</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    <artifactId>root-cause-engine</artifactId>
    <name>root-cause-engine</name>
    <description>Root cause analysis using Easy Rules chain</description>
    <dependencies>
        <dependency>
            <groupId>com.ailog</groupId>
            <artifactId>common</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.ailog</groupId>
            <artifactId>context-builder</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.jeasy</groupId>
            <artifactId>easy-rules-core</artifactId>
            <version>4.1.0</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Commit**

```bash
git add root-cause-engine/pom.xml
git commit -m "chore(root-cause): add easy-rules and context-builder dependencies"
```

---

### Task 2: RootCause enum

**Files:**
- Create: `root-cause-engine/src/main/java/com/ailog/rootcause/RootCause.java`
- Test: none (pure enum)

- [ ] **Step 1: Create RootCause.java**

```java
package com.ailog.rootcause;

public enum RootCause {
    CODE_DEFECT,
    RESOURCE_EXHAUSTION,
    CONFIG_ERROR,
    DB_PERFORMANCE,
    DEPENDENCY_FAILURE,
    UNKNOWN
}
```

- [ ] **Step 2: Commit**

```bash
git add root-cause-engine/src/main/java/com/ailog/rootcause/RootCause.java
git commit -m "feat(root-cause): add RootCause enum"
```

---

### Task 3: RootCauseReport model

**Files:**
- Create: `root-cause-engine/src/main/java/com/ailog/rootcause/RootCauseReport.java`
- Test: none (pure POJO)

- [ ] **Step 1: Create RootCauseReport.java**

```java
package com.ailog.rootcause;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class RootCauseReport {

    private final String eventId;
    private final RootCause rootCause;
    private final List<String> evidence;
    private final String affectedService;
    private final String suggestedAction;
    private final Instant analyzedAt;

    public RootCauseReport(String eventId, RootCause rootCause, List<String> evidence,
                           String affectedService, String suggestedAction) {
        this.eventId = eventId;
        this.rootCause = rootCause;
        this.evidence = new ArrayList<>(evidence);
        this.affectedService = affectedService;
        this.suggestedAction = suggestedAction;
        this.analyzedAt = Instant.now();
    }

    public static RootCauseReport unknown(String eventId) {
        return new RootCauseReport(eventId, RootCause.UNKNOWN, List.of(), "unknown", "无法识别根因，请人工排查");
    }

    public String getEventId() { return eventId; }
    public RootCause getRootCause() { return rootCause; }
    public List<String> getEvidence() { return evidence; }
    public String getAffectedService() { return affectedService; }
    public String getSuggestedAction() { return suggestedAction; }
    public Instant getAnalyzedAt() { return analyzedAt; }
}
```

- [ ] **Step 2: Commit**

```bash
git add root-cause-engine/src/main/java/com/ailog/rootcause/RootCauseReport.java
git commit -m "feat(root-cause): add RootCauseReport model"
```

---

### Task 4: RootCauseAnalyzer — Easy Rule adapter

Wraps Easy Rules `Rule` interface for Spring-friendly injection.

**Files:**
- Create: `root-cause-engine/src/main/java/com/ailog/rootcause/RootCauseAnalyzer.java`
- Test: none (interface, tested via concrete rules)

- [ ] **Step 1: Create RootCauseAnalyzer.java**

```java
package com.ailog.rootcause;

import com.ailog.context.ContextPackage;
import org.jeasy.rules.api.Rule;

public interface RootCauseAnalyzer extends Rule {

    RootCause rootCause();

    boolean evaluate(ContextPackage context);

    RootCauseReport execute(ContextPackage context);
}
```

- [ ] **Step 2: Commit**

```bash
git add root-cause-engine/src/main/java/com/ailog/rootcause/RootCauseAnalyzer.java
git commit -m "feat(root-cause): add RootCauseAnalyzer interface wrapping Easy Rule"
```

---

### Task 5: CodeDefectRule

**Files:**
- Create: `root-cause-engine/src/main/java/com/ailog/rootcause/rule/CodeDefectRule.java`
- Test: `root-cause-engine/src/test/java/com/ailog/rootcause/rule/CodeDefectRuleTest.java`

- [ ] **Step 1: Write the failing test**

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl root-cause-engine -Dtest=CodeDefectRuleTest -q`
Expected: FAIL

- [ ] **Step 3: Write minimal implementation**

```java
package com.ailog.rootcause.rule;

import com.ailog.context.ContextData;
import com.ailog.context.ContextPackage;
import com.ailog.rootcause.RootCause;
import com.ailog.rootcause.RootCauseAnalyzer;
import com.ailog.rootcause.RootCauseReport;
import org.jeasy.rules.api.Facts;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CodeDefectRule implements RootCauseAnalyzer {

    @Override
    public RootCause rootCause() {
        return RootCause.CODE_DEFECT;
    }

    @Override
    public boolean evaluate(ContextPackage context) {
        String exceptionType = context.getOriginalEvent().getExceptionType();
        ContextData gitData = context.getContexts().get("git");
        return exceptionType != null && !exceptionType.isBlank()
                && gitData != null && gitData.isSuccess()
                && gitData.getData().containsKey("recentCommits");
    }

    @Override
    public RootCauseReport execute(ContextPackage context) {
        List<String> evidence = new ArrayList<>();
        Event event = context.getOriginalEvent();
        evidence.add(event.getServiceName() + " 出现 " + event.getExceptionType()
                + "：" + (event.getErrorMessage() != null ? event.getErrorMessage() : ""));

        ContextData gitData = context.getContexts().get("git");
        if (gitData != null && gitData.isSuccess()) {
            String commits = (String) gitData.getData().get("recentCommits");
            if (commits != null && !commits.isBlank()) {
                evidence.add("Git 记录显示近期提交：" + commits);
            }
        }

        return new RootCauseReport(
                event.getId(),
                RootCause.CODE_DEFECT,
                evidence,
                event.getServiceName(),
                "审查最近代码变更并回滚或修复"
        );
    }

    // --- Easy Rules boilerplate ---
    @Override public String getName() { return "codeDefectRule"; }
    @Override public String getDescription() { return "检测代码缺陷导致的异常"; }
    @Override public int getPriority() { return 1; }
    @Override public boolean evaluate(Facts facts) { return evaluate((ContextPackage) facts.get("context")); }
    @Override public void execute(Facts facts) { facts.put("report", execute((ContextPackage) facts.get("context"))); }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl root-cause-engine -Dtest=CodeDefectRuleTest -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add root-cause-engine/src/main/java/com/ailog/rootcause/rule/CodeDefectRule.java root-cause-engine/src/test/java/com/ailog/rootcause/rule/CodeDefectRuleTest.java
git commit -m "feat(root-cause): add CodeDefectRule (layer 1)"
```

---

### Task 6: ResourceExhaustionRule

**Files:**
- Create: `root-cause-engine/src/main/java/com/ailog/rootcause/rule/ResourceExhaustionRule.java`
- Test: `root-cause-engine/src/test/java/com/ailog/rootcause/rule/ResourceExhaustionRuleTest.java`

- [ ] **Step 1: Write the failing test**

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl root-cause-engine -Dtest=ResourceExhaustionRuleTest -q`
Expected: FAIL

- [ ] **Step 3: Write minimal implementation**

```java
package com.ailog.rootcause.rule;

import com.ailog.context.ContextData;
import com.ailog.context.ContextPackage;
import com.ailog.rootcause.RootCause;
import com.ailog.rootcause.RootCauseAnalyzer;
import com.ailog.rootcause.RootCauseReport;
import org.jeasy.rules.api.Facts;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ResourceExhaustionRule implements RootCauseAnalyzer {

    @Override
    public RootCause rootCause() {
        return RootCause.RESOURCE_EXHAUSTION;
    }

    @Override
    public boolean evaluate(ContextPackage context) {
        ContextData dockerData = context.getContexts().get("docker");
        if (dockerData != null && dockerData.isSuccess()) {
            String logs = (String) dockerData.getData().get("logs");
            if (logs != null && (logs.contains("OOM") || logs.contains("oom") || logs.contains("kill"))) {
                return true;
            }
        }

        ContextData redisData = context.getContexts().get("redis");
        if (redisData != null && redisData.isSuccess()) {
            String memory = (String) redisData.getData().get("memory");
            if (memory != null && memory.contains("used_memory_rss")) {
                return true;
            }
        }

        return false;
    }

    @Override
    public RootCauseReport execute(ContextPackage context) {
        List<String> evidence = new ArrayList<>();
        Event event = context.getOriginalEvent();
        evidence.add("服务 " + event.getServiceName() + " 触发 " + event.getExceptionType() + " 告警");

        ContextData dockerData = context.getContexts().get("docker");
        if (dockerData != null && dockerData.isSuccess()) {
            String logs = (String) dockerData.getData().get("logs");
            if (logs != null && logs.contains("OOM")) {
                evidence.add("Docker 容器因 OOM 被杀死");
            }
        }

        ContextData redisData = context.getContexts().get("redis");
        if (redisData != null && redisData.isSuccess()) {
            String memory = (String) redisData.getData().get("memory");
            if (memory != null) {
                evidence.add("Redis 内存告警：" + memory);
            }
        }

        return new RootCauseReport(
                event.getId(),
                RootCause.RESOURCE_EXHAUSTION,
                evidence,
                event.getServiceName(),
                "检查资源限制并扩容，或排查内存泄漏"
        );
    }

    @Override public String getName() { return "resourceExhaustionRule"; }
    @Override public String getDescription() { return "检测资源耗尽导致的故障"; }
    @Override public int getPriority() { return 2; }
    @Override public boolean evaluate(Facts facts) { return evaluate((ContextPackage) facts.get("context")); }
    @Override public void execute(Facts facts) { facts.put("report", execute((ContextPackage) facts.get("context"))); }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl root-cause-engine -Dtest=ResourceExhaustionRuleTest -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add root-cause-engine/src/main/java/com/ailog/rootcause/rule/ResourceExhaustionRule.java root-cause-engine/src/test/java/com/ailog/rootcause/rule/ResourceExhaustionRuleTest.java
git commit -m "feat(root-cause): add ResourceExhaustionRule (layer 1)"
```

---

### Task 7: ConfigErrorRule

**Files:**
- Create: `root-cause-engine/src/main/java/com/ailog/rootcause/rule/ConfigErrorRule.java`
- Test: `root-cause-engine/src/test/java/com/ailog/rootcause/rule/ConfigErrorRuleTest.java`

- [ ] **Step 1: Write the failing test**

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl root-cause-engine -Dtest=ConfigErrorRuleTest -q`
Expected: FAIL

- [ ] **Step 3: Write minimal implementation**

```java
package com.ailog.rootcause.rule;

import com.ailog.context.ContextPackage;
import com.ailog.rootcause.RootCause;
import com.ailog.rootcause.RootCauseAnalyzer;
import com.ailog.rootcause.RootCauseReport;
import org.jeasy.rules.api.Facts;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ConfigErrorRule implements RootCauseAnalyzer {

    private static final List<String> CONFIG_KEYWORDS = List.of("config", "FileNotFound", "NoSuchFile",
            "application.yml", "application.properties", "not found", "InvalidConfig");

    @Override
    public RootCause rootCause() {
        return RootCause.CONFIG_ERROR;
    }

    @Override
    public boolean evaluate(ContextPackage context) {
        String errorMessage = context.getOriginalEvent().getErrorMessage();
        if (errorMessage == null || errorMessage.isBlank()) return false;

        return CONFIG_KEYWORDS.stream().anyMatch(kw ->
                errorMessage.toLowerCase().contains(kw.toLowerCase()));
    }

    @Override
    public RootCauseReport execute(ContextPackage context) {
        List<String> evidence = new ArrayList<>();
        Event event = context.getOriginalEvent();
        evidence.add("错误信息：" + event.getErrorMessage());
        evidence.add("可能存在配置缺失或路径错误，建议检查配置文件与环境变量");

        return new RootCauseReport(
                event.getId(),
                RootCause.CONFIG_ERROR,
                evidence,
                event.getServiceName(),
                "检查配置文件路径与内容是否正确"
        );
    }

    @Override public String getName() { return "configErrorRule"; }
    @Override public String getDescription() { return "检测配置错误导致的异常"; }
    @Override public int getPriority() { return 3; }
    @Override public boolean evaluate(Facts facts) { return evaluate((ContextPackage) facts.get("context")); }
    @Override public void execute(Facts facts) { facts.put("report", execute((ContextPackage) facts.get("context"))); }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl root-cause-engine -Dtest=ConfigErrorRuleTest -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add root-cause-engine/src/main/java/com/ailog/rootcause/rule/ConfigErrorRule.java root-cause-engine/src/test/java/com/ailog/rootcause/rule/ConfigErrorRuleTest.java
git commit -m "feat(root-cause): add ConfigErrorRule (layer 1)"
```

---

### Task 8: DbPerformanceRule

**Files:**
- Create: `root-cause-engine/src/main/java/com/ailog/rootcause/rule/DbPerformanceRule.java`
- Test: `root-cause-engine/src/test/java/com/ailog/rootcause/rule/DbPerformanceRuleTest.java`

- [ ] **Step 1: Write the failing test**

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl root-cause-engine -Dtest=DbPerformanceRuleTest -q`
Expected: FAIL

- [ ] **Step 3: Write minimal implementation**

```java
package com.ailog.rootcause.rule;

import com.ailog.context.ContextData;
import com.ailog.context.ContextPackage;
import com.ailog.rootcause.RootCause;
import com.ailog.rootcause.RootCauseAnalyzer;
import com.ailog.rootcause.RootCauseReport;
import org.jeasy.rules.api.Facts;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DbPerformanceRule implements RootCauseAnalyzer {

    @Override
    public RootCause rootCause() {
        return RootCause.DB_PERFORMANCE;
    }

    @Override
    public boolean evaluate(ContextPackage context) {
        if (context.getOriginalEvent().getType() != EventType.DB_SLOW_QUERY) return false;

        ContextData dbData = context.getContexts().get("db");
        return dbData != null && dbData.isSuccess()
                && dbData.getData().containsKey("slowSql");
    }

    @Override
    public RootCauseReport execute(ContextPackage context) {
        List<String> evidence = new ArrayList<>();
        Event event = context.getOriginalEvent();
        evidence.add("检测到数据库慢查询");

        ContextData dbData = context.getContexts().get("db");
        if (dbData != null && dbData.isSuccess()) {
            String slowSql = (String) dbData.getData().get("slowSql");
            if (slowSql != null) evidence.add("慢查询详情：" + slowSql);

            String locks = (String) dbData.getData().get("locks");
            if (locks != null && !locks.isBlank()) evidence.add("当前锁信息：" + locks);
        }

        return new RootCauseReport(
                event.getId(),
                RootCause.DB_PERFORMANCE,
                evidence,
                "mysql",
                "检查慢查询 SQL 并优化索引或查询语句"
        );
    }

    @Override public String getName() { return "dbPerformanceRule"; }
    @Override public String getDescription() { return "检测数据库性能瓶颈"; }
    @Override public int getPriority() { return 4; }
    @Override public boolean evaluate(Facts facts) { return evaluate((ContextPackage) facts.get("context")); }
    @Override public void execute(Facts facts) { facts.put("report", execute((ContextPackage) facts.get("context"))); }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl root-cause-engine -Dtest=DbPerformanceRuleTest -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add root-cause-engine/src/main/java/com/ailog/rootcause/rule/DbPerformanceRule.java root-cause-engine/src/test/java/com/ailog/rootcause/rule/DbPerformanceRuleTest.java
git commit -m "feat(root-cause): add DbPerformanceRule (layer 2)"
```

---

### Task 9: DependencyFailureRule

**Files:**
- Create: `root-cause-engine/src/main/java/com/ailog/rootcause/rule/DependencyFailureRule.java`
- Test: `root-cause-engine/src/test/java/com/ailog/rootcause/rule/DependencyFailureRuleTest.java`

- [ ] **Step 1: Write the failing test**

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl root-cause-engine -Dtest=DependencyFailureRuleTest -q`
Expected: FAIL

- [ ] **Step 3: Write minimal implementation**

```java
package com.ailog.rootcause.rule;

import com.ailog.context.ContextData;
import com.ailog.context.ContextPackage;
import com.ailog.rootcause.RootCause;
import com.ailog.rootcause.RootCauseAnalyzer;
import com.ailog.rootcause.RootCauseReport;
import org.jeasy.rules.api.Facts;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DependencyFailureRule implements RootCauseAnalyzer {

    @Override
    public RootCause rootCause() {
        return RootCause.DEPENDENCY_FAILURE;
    }

    @Override
    public boolean evaluate(ContextPackage context) {
        if (context.getOriginalEvent().getType() != EventType.MQ_LAG) return false;

        ContextData mqData = context.getContexts().get("mq");
        return mqData != null && mqData.isSuccess()
                && mqData.getData().containsKey("lag");
    }

    @Override
    public RootCauseReport execute(ContextPackage context) {
        List<String> evidence = new ArrayList<>();
        Event event = context.getOriginalEvent();
        evidence.add("消息队列消费延迟，可能导致依赖服务故障");

        ContextData mqData = context.getContexts().get("mq");
        if (mqData != null && mqData.isSuccess()) {
            String lag = (String) mqData.getData().get("lag");
            if (lag != null) evidence.add("消费延迟：" + lag);

            String retry = (String) mqData.getData().get("retry");
            if (retry != null && !retry.isBlank()) evidence.add("重试队列：" + retry);
        }

        return new RootCauseReport(
                event.getId(),
                RootCause.DEPENDENCY_FAILURE,
                evidence,
                "rocketmq",
                "检查 RocketMQ 消费者健康状况并重启或扩容"
        );
    }

    @Override public String getName() { return "dependencyFailureRule"; }
    @Override public String getDescription() { return "检测依赖服务故障"; }
    @Override public int getPriority() { return 5; }
    @Override public boolean evaluate(Facts facts) { return evaluate((ContextPackage) facts.get("context")); }
    @Override public void execute(Facts facts) { facts.put("report", execute((ContextPackage) facts.get("context"))); }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl root-cause-engine -Dtest=DependencyFailureRuleTest -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add root-cause-engine/src/main/java/com/ailog/rootcause/rule/DependencyFailureRule.java root-cause-engine/src/test/java/com/ailog/rootcause/rule/DependencyFailureRuleTest.java
git commit -m "feat(root-cause): add DependencyFailureRule (layer 2)"
```

---

### Task 10: RootCauseEngineService — orchestrator

**Files:**
- Create: `root-cause-engine/src/main/java/com/ailog/rootcause/RootCauseEngineService.java`
- Test: `root-cause-engine/src/test/java/com/ailog/rootcause/RootCauseEngineServiceTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.ailog.rootcause;

import com.ailog.common.event.Event;
import com.ailog.common.event.EventType;
import com.ailog.common.event.Severity;
import com.ailog.context.ContextData;
import com.ailog.context.ContextPackage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
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

        RootCauseEngineService service = new RootCauseEngineService();
        RootCauseReport report = service.analyze(pkg);

        assertEquals(RootCause.CODE_DEFECT, report.getRootCause());
    }

    @Test
    void shouldReturnUnknownWhenNoRuleMatches() {
        Event event = new Event(UUID.randomUUID().toString(), EventType.LOG_ERROR, "unknown",
                null, null, Instant.now(), "source", Severity.LOW, Map.of());

        ContextPackage pkg = new ContextPackage(event);

        RootCauseEngineService service = new RootCauseEngineService();
        RootCauseReport report = service.analyze(pkg);

        assertEquals(RootCause.UNKNOWN, report.getRootCause());
    }

    @Test
    void shouldReturnDbPerformanceWhenNoLayer1ButLayer2Matches() {
        Event event = new Event(UUID.randomUUID().toString(), EventType.DB_SLOW_QUERY, "mysql",
                "SlowQuery", "5s query", Instant.now(), "mysql", Severity.MEDIUM, Map.of());

        ContextPackage pkg = new ContextPackage(event);
        pkg.addContext(ContextData.success("db", Map.of("slowSql", "5s SELECT * FROM orders")));

        RootCauseEngineService service = new RootCauseEngineService();
        RootCauseReport report = service.analyze(pkg);

        assertEquals(RootCause.DB_PERFORMANCE, report.getRootCause());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl root-cause-engine -Dtest=RootCauseEngineServiceTest -q`
Expected: FAIL

- [ ] **Step 3: Write minimal implementation**

```java
package com.ailog.rootcause;

import com.ailog.context.ContextPackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RootCauseEngineService {

    private static final Logger log = LoggerFactory.getLogger(RootCauseEngineService.class);

    private final List<RootCauseAnalyzer> analyzers;

    public RootCauseEngineService(List<RootCauseAnalyzer> analyzers) {
        this.analyzers = analyzers;
    }

    public RootCauseReport analyze(ContextPackage context) {
        String eventId = context.getOriginalEvent().getId();
        RootCauseReport layer1Match = null;

        for (RootCauseAnalyzer analyzer : analyzers) {
            try {
                if (!analyzer.evaluate(context)) continue;

                RootCauseReport report = analyzer.execute(context);
                RootCause cause = report.getRootCause();

                // Layer 1 (priority < 4): direct cause, return immediately
                if (cause == RootCause.CODE_DEFECT
                        || cause == RootCause.RESOURCE_EXHAUSTION
                        || cause == RootCause.CONFIG_ERROR) {
                    log.info("Root cause identified: {} for event {}", cause, eventId);
                    return report;
                }

                // Layer 2: track best match by evidence length
                if (layer1Match == null || report.getEvidence().size() > layer1Match.getEvidence().size()) {
                    layer1Match = report;
                }
            } catch (Exception e) {
                log.warn("Analyzer {} failed for event {}: {}", analyzer.getName(), eventId, e.getMessage());
            }
        }

        if (layer1Match != null) {
            log.info("Root cause identified (layer 2): {} for event {}", layer1Match.getRootCause(), eventId);
            return layer1Match;
        }

        log.warn("No root cause identified for event {}", eventId);
        return RootCauseReport.unknown(eventId);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl root-cause-engine -Dtest=RootCauseEngineServiceTest -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add root-cause-engine/src/main/java/com/ailog/rootcause/RootCauseEngineService.java root-cause-engine/src/test/java/com/ailog/rootcause/RootCauseEngineServiceTest.java
git commit -m "feat(root-cause): add RootCauseEngineService orchestrator with two-layer chain"
```

---

## Self-Review

**Spec coverage check:**
- RootCause enum → Task 2
- RootCauseReport model → Task 3
- RootCauseAnalyzer interface → Task 4
- CodeDefectRule (layer 1) → Task 5
- ResourceExhaustionRule (layer 1) → Task 6
- ConfigErrorRule (layer 1) → Task 7
- DbPerformanceRule (layer 2) → Task 8
- DependencyFailureRule (layer 2) → Task 9
- RootCauseEngineService with two-layer logic → Task 10
- Easy Rules dependency → Task 1
- context-builder dependency → Task 1

**Placeholder scan:** OK — all steps contain complete code

**Type consistency:** `RootCauseAnalyzer.rootCause()` returns `RootCause` across all rules. `RootCauseEngineService.analyze()` returns `RootCauseReport`. `RootCauseReport.unknown()` static factory used correctly. All priorities match the spec design.
