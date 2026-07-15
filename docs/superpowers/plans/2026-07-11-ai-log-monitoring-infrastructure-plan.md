# AI Log Monitoring — 第一期基础设施实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 搭建 10 模块 Maven 多模块项目骨架，包括共享模型、状态机编排器和 Dashboard API 基础。

**Architecture:** 模块化单体架构。`orchestrator` 是主 Spring Boot 入口，依赖所有其他模块并通过 `scanBasePackages` 扫描全量组件。`common` 提供共享 Event 模型和 `EventBus` 接口。`dashboard` 提供 REST API + WebSocket。其余 6 个引擎模块 + `mcp-tool-center` 为纯库模块。

**Tech Stack:** Java 21 + Spring Boot 3.4.4 + Spring AI 1.0.0-M6 + Maven

---

### Task 1: 创建 Parent POM 和目录结构

**Files:**
- Create: `pom.xml`
- Create: `common/src/main/java/com/ailog/common/`
- Create: `common/src/test/java/com/ailog/common/`
- Create: `observe-engine/src/main/java/com/ailog/observe/`
- Create: `observe-engine/src/test/java/com/ailog/observe/`
- Create: `context-builder/src/main/java/com/ailog/context/`
- Create: `context-builder/src/test/java/com/ailog/context/`
- Create: `code-knowledge-graph/src/main/java/com/ailog/knowledge/`
- Create: `code-knowledge-graph/src/test/java/com/ailog/knowledge/`
- Create: `root-cause-engine/src/main/java/com/ailog/rootcause/`
- Create: `root-cause-engine/src/test/java/com/ailog/rootcause/`
- Create: `planner/src/main/java/com/ailog/planner/`
- Create: `planner/src/test/java/com/ailog/planner/`
- Create: `executor/src/main/java/com/ailog/executor/`
- Create: `executor/src/test/java/com/ailog/executor/`
- Create: `mcp-tool-center/src/main/java/com/ailog/mcp/`
- Create: `mcp-tool-center/src/test/java/com/ailog/mcp/`
- Create: `dashboard/src/main/java/com/ailog/dashboard/`
- Create: `dashboard/src/test/java/com/ailog/dashboard/`
- Create: `orchestrator/src/main/java/com/ailog/orchestrator/`
- Create: `orchestrator/src/test/java/com/ailog/orchestrator/`

- [ ] **Step 1: Create all directories**

```bash
mkdir -p "D:\Claude-Create-Project\ai-log-monitoring\common\src\main\java\com\ailog\common\event"
mkdir -p "D:\Claude-Create-Project\ai-log-monitoring\common\src\main\java\com\ailog\common\model"
mkdir -p "D:\Claude-Create-Project\ai-log-monitoring\common\src\test\java\com\ailog\common"
mkdir -p "D:\Claude-Create-Project\ai-log-monitoring\observe-engine\src\main\java\com\ailog\observe"
mkdir -p "D:\Claude-Create-Project\ai-log-monitoring\observe-engine\src\test\java\com\ailog\observe"
mkdir -p "D:\Claude-Create-Project\ai-log-monitoring\context-builder\src\main\java\com\ailog\context"
mkdir -p "D:\Claude-Create-Project\ai-log-monitoring\context-builder\src\test\java\com\ailog\context"
mkdir -p "D:\Claude-Create-Project\ai-log-monitoring\code-knowledge-graph\src\main\java\com\ailog\knowledge"
mkdir -p "D:\Claude-Create-Project\ai-log-monitoring\code-knowledge-graph\src\test\java\com\ailog\knowledge"
mkdir -p "D:\Claude-Create-Project\ai-log-monitoring\root-cause-engine\src\main\java\com\ailog\rootcause"
mkdir -p "D:\Claude-Create-Project\ai-log-monitoring\root-cause-engine\src\test\java\com\ailog\rootcause"
mkdir -p "D:\Claude-Create-Project\ai-log-monitoring\planner\src\main\java\com\ailog\planner"
mkdir -p "D:\Claude-Create-Project\ai-log-monitoring\planner\src\test\java\com\ailog\planner"
mkdir -p "D:\Claude-Create-Project\ai-log-monitoring\executor\src\main\java\com\ailog\executor"
mkdir -p "D:\Claude-Create-Project\ai-log-monitoring\executor\src\test\java\com\ailog\executor"
mkdir -p "D:\Claude-Create-Project\ai-log-monitoring\mcp-tool-center\src\main\java\com\ailog\mcp"
mkdir -p "D:\Claude-Create-Project\ai-log-monitoring\mcp-tool-center\src\test\java\com\ailog\mcp"
mkdir -p "D:\Claude-Create-Project\ai-log-monitoring\dashboard\src\main\java\com\ailog\dashboard\controller"
mkdir -p "D:\Claude-Create-Project\ai-log-monitoring\dashboard\src\main\java\com\ailog\dashboard\config"
mkdir -p "D:\Claude-Create-Project\ai-log-monitoring\dashboard\src\test\java\com\ailog\dashboard"
mkdir -p "D:\Claude-Create-Project\ai-log-monitoring\orchestrator\src\main\java\com\ailog\orchestrator\state"
mkdir -p "D:\Claude-Create-Project\ai-log-monitoring\orchestrator\src\main\java\com\ailog\orchestrator\service"
mkdir -p "D:\Claude-Create-Project\ai-log-monitoring\orchestrator\src\test\java\com\ailog\orchestrator"
```

- [ ] **Step 2: Create parent pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.4</version>
        <relativePath/>
    </parent>

    <groupId>com.ailog</groupId>
    <artifactId>ai-log-monitoring</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <packaging>pom</packaging>
    <name>ai-log-monitoring</name>
    <description>AIOps Software Engineering Agent Platform</description>

    <properties>
        <java.version>21</java.version>
        <spring-ai.version>1.0.0-M6</spring-ai.version>
    </properties>

    <modules>
        <module>common</module>
        <module>observe-engine</module>
        <module>context-builder</module>
        <module>code-knowledge-graph</module>
        <module>root-cause-engine</module>
        <module>planner</module>
        <module>executor</module>
        <module>mcp-tool-center</module>
        <module>dashboard</module>
        <module>orchestrator</module>
    </modules>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>${spring-ai.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

- [ ] **Step 3: Verify parent POM structure**

Run: `dir "D:\Claude-Create-Project\ai-log-monitoring"`
Expected: Shows root pom.xml + all 10 module directories

---

### Task 2: 创建 common 模块（共享模型 + Event 总线）

**Files:**
- Create: `common/pom.xml`
- Create: `common/src/main/java/com/ailog/common/event/Event.java`
- Create: `common/src/main/java/com/ailog/common/event/EventType.java`
- Create: `common/src/main/java/com/ailog/common/event/Severity.java`
- Create: `common/src/main/java/com/ailog/common/model/Result.java`
- Create: `common/src/main/java/com/ailog/common/event/EventBus.java`

- [ ] **Step 1: Create common/pom.xml**

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

    <artifactId>common</artifactId>
    <name>common</name>
    <description>Shared models and utilities</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Create EventType.java**

```java
package com.ailog.common.event;

public enum EventType {
    LOG_ERROR,
    LOG_WARN,
    METRIC_ALERT,
    TRACE_ANOMALY,
    DEPLOY_EVENT,
    DB_SLOW_QUERY,
    REDIS_FAILURE,
    MQ_LAG,
    DOCKER_CRASH,
    K8S_EVENT,
    CI_FAILURE,
    CONFIG_CHANGE
}
```

- [ ] **Step 3: Create Severity.java**

```java
package com.ailog.common.event;

public enum Severity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    INFO
}
```

- [ ] **Step 4: Create Event.java**

```java
package com.ailog.common.event;

import java.time.Instant;
import java.util.Map;

public class Event {

    private String id;
    private EventType type;
    private Instant time;
    private String source;
    private Severity severity;
    private Map<String, Object> payload;

    public Event() {
    }

    public Event(String id, EventType type, Instant time, String source, Severity severity, Map<String, Object> payload) {
        this.id = id;
        this.type = type;
        this.time = time;
        this.source = source;
        this.severity = severity;
        this.payload = payload;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public EventType getType() {
        return type;
    }

    public void setType(EventType type) {
        this.type = type;
    }

    public Instant getTime() {
        return time;
    }

    public void setTime(Instant time) {
        this.time = time;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }
}
```

- [ ] **Step 5: Create Result.java**

```java
package com.ailog.common.model;

public class Result<T> {

    private int code;
    private String message;
    private T data;

    public Result() {
    }

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(0, "success", data);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
```

- [ ] **Step 6: Create EventBus.java**

```java
package com.ailog.common.event;

public interface EventBus {

    void publish(Event event);

    void subscribe(EventType type, EventHandler handler);

    @FunctionalInterface
    interface EventHandler {
        void handle(Event event);
    }
}
```

- [ ] **Step 7: Verify common module compiles**

Run: `mvn clean compile -pl common -f "D:\Claude-Create-Project\ai-log-monitoring\pom.xml"`
Expected: BUILD SUCCESS

---

### Task 3: 创建 7 个骨架模块（observe-engine / context-builder / code-knowledge-graph / root-cause-engine / planner / executor / mcp-tool-center）

**Files:**
- Create: `observe-engine/pom.xml`
- Create: `observe-engine/src/main/java/com/ailog/observe/ObserveEngineConfig.java`
- Create: `observe-engine/src/main/resources/application.yml`
- Create: `context-builder/pom.xml`
- Create: `context-builder/src/main/java/com/ailog/context/ContextBuilderConfig.java`
- Create: `context-builder/src/main/resources/application.yml`
- Create: `code-knowledge-graph/pom.xml`
- Create: `code-knowledge-graph/src/main/java/com/ailog/knowledge/CodeKnowledgeGraphConfig.java`
- Create: `code-knowledge-graph/src/main/resources/application.yml`
- Create: `root-cause-engine/pom.xml`
- Create: `root-cause-engine/src/main/java/com/ailog/rootcause/RootCauseEngineConfig.java`
- Create: `root-cause-engine/src/main/resources/application.yml`
- Create: `planner/pom.xml`
- Create: `planner/src/main/java/com/ailog/planner/PlannerConfig.java`
- Create: `planner/src/main/resources/application.yml`
- Create: `executor/pom.xml`
- Create: `executor/src/main/java/com/ailog/executor/ExecutorConfig.java`
- Create: `executor/src/main/resources/application.yml`
- Create: `mcp-tool-center/pom.xml`
- Create: `mcp-tool-center/src/main/java/com/ailog/mcp/McpToolCenterConfig.java`
- Create: `mcp-tool-center/src/main/resources/application.yml`

- [ ] **Step 1: Create observe-engine module files**

**observe-engine/pom.xml:**
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

    <artifactId>observe-engine</artifactId>
    <name>observe-engine</name>
    <description>Event observation engine - listens to logs, traces, metrics, and infrastructure events</description>

    <dependencies>
        <dependency>
            <groupId>com.ailog</groupId>
            <artifactId>common</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
</project>
```

**observe-engine/src/main/java/com/ailog/observe/ObserveEngineConfig.java:**
```java
package com.ailog.observe;

import org.springframework.context.annotation.Configuration;

@Configuration
public class ObserveEngineConfig {
}
```

**observe-engine/src/main/resources/application.yml:**
```yaml
observe-engine:
  enabled: true
```

- [ ] **Step 2: Create context-builder module files**

**context-builder/pom.xml:**
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

    <artifactId>context-builder</artifactId>
    <name>context-builder</name>
    <description>Builds context package from events (trace, git, code, config, env)</description>

    <dependencies>
        <dependency>
            <groupId>com.ailog</groupId>
            <artifactId>common</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
</project>
```

**context-builder/src/main/java/com/ailog/context/ContextBuilderConfig.java:**
```java
package com.ailog.context;

import org.springframework.context.annotation.Configuration;

@Configuration
public class ContextBuilderConfig {
}
```

**context-builder/src/main/resources/application.yml:**
```yaml
context-builder:
  enabled: true
```

- [ ] **Step 3: Create code-knowledge-graph module files**

**code-knowledge-graph/pom.xml:**
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

    <artifactId>code-knowledge-graph</artifactId>
    <name>code-knowledge-graph</name>
    <description>Code knowledge graph - class, method, SQL, API relationships</description>

    <dependencies>
        <dependency>
            <groupId>com.ailog</groupId>
            <artifactId>common</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
</project>
```

**code-knowledge-graph/src/main/java/com/ailog/knowledge/CodeKnowledgeGraphConfig.java:**
```java
package com.ailog.knowledge;

import org.springframework.context.annotation.Configuration;

@Configuration
public class CodeKnowledgeGraphConfig {
}
```

**code-knowledge-graph/src/main/resources/application.yml:**
```yaml
code-knowledge-graph:
  enabled: true
```

- [ ] **Step 4: Create root-cause-engine module files**

**root-cause-engine/pom.xml:**
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
    <description>Root cause analysis using tree search</description>

    <dependencies>
        <dependency>
            <groupId>com.ailog</groupId>
            <artifactId>common</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
</project>
```

**root-cause-engine/src/main/java/com/ailog/rootcause/RootCauseEngineConfig.java:**
```java
package com.ailog.rootcause;

import org.springframework.context.annotation.Configuration;

@Configuration
public class RootCauseEngineConfig {
}
```

**root-cause-engine/src/main/resources/application.yml:**
```yaml
root-cause-engine:
  enabled: true
```

- [ ] **Step 5: Create planner module files**

**planner/pom.xml:**
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

    <artifactId>planner</artifactId>
    <name>planner</name>
    <description>Generates and evaluates repair plans from root cause</description>

    <dependencies>
        <dependency>
            <groupId>com.ailog</groupId>
            <artifactId>common</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
</project>
```

**planner/src/main/java/com/ailog/planner/PlannerConfig.java:**
```java
package com.ailog.planner;

import org.springframework.context.annotation.Configuration;

@Configuration
public class PlannerConfig {
}
```

**planner/src/main/resources/application.yml:**
```yaml
planner:
  enabled: true
```

- [ ] **Step 6: Create executor module files**

**executor/pom.xml:**
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

    <artifactId>executor</artifactId>
    <name>executor</name>
    <description>Workflow executor - git checkout, code modification, test, build</description>

    <dependencies>
        <dependency>
            <groupId>com.ailog</groupId>
            <artifactId>common</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
</project>
```

**executor/src/main/java/com/ailog/executor/ExecutorConfig.java:**
```java
package com.ailog.executor;

import org.springframework.context.annotation.Configuration;

@Configuration
public class ExecutorConfig {
}
```

**executor/src/main/resources/application.yml:**
```yaml
executor:
  enabled: true
```

- [ ] **Step 7: Create mcp-tool-center module files**

**mcp-tool-center/pom.xml:**
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

    <artifactId>mcp-tool-center</artifactId>
    <name>mcp-tool-center</name>
    <description>Unified MCP tool layer - Docker, MySQL, Redis, MQ tool wrappers</description>

    <dependencies>
        <dependency>
            <groupId>com.ailog</groupId>
            <artifactId>common</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
</project>
```

**mcp-tool-center/src/main/java/com/ailog/mcp/McpToolCenterConfig.java:**
```java
package com.ailog.mcp;

import org.springframework.context.annotation.Configuration;

@Configuration
public class McpToolCenterConfig {
}
```

**mcp-tool-center/src/main/resources/application.yml:**
```yaml
mcp-tool-center:
  enabled: true
```

- [ ] **Step 8: Verify all skeleton modules compile**

```bash
mvn clean compile -pl observe-engine,context-builder,code-knowledge-graph,root-cause-engine,planner,executor,mcp-tool-center -f "D:\Claude-Create-Project\ai-log-monitoring\pom.xml"
```

Expected: BUILD SUCCESS

---

### Task 4: 创建 orchestrator 模块（Spring Boot 主入口 + 状态机）

**Files:**
- Create: `orchestrator/pom.xml`
- Create: `orchestrator/src/main/java/com/ailog/orchestrator/OrchestratorApplication.java`
- Create: `orchestrator/src/main/java/com/ailog/orchestrator/state/WorkflowState.java`
- Create: `orchestrator/src/main/java/com/ailog/orchestrator/state/StateMachine.java`
- Create: `orchestrator/src/main/java/com/ailog/orchestrator/service/OrchestrationService.java`
- Create: `orchestrator/src/main/resources/application.yml`

- [ ] **Step 1: Create orchestrator/pom.xml**

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

    <artifactId>orchestrator</artifactId>
    <name>orchestrator</name>
    <description>AI state machine orchestrator - connects all modules</description>

    <dependencies>
        <dependency>
            <groupId>com.ailog</groupId>
            <artifactId>common</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.ailog</groupId>
            <artifactId>observe-engine</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.ailog</groupId>
            <artifactId>context-builder</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.ailog</groupId>
            <artifactId>code-knowledge-graph</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.ailog</groupId>
            <artifactId>root-cause-engine</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.ailog</groupId>
            <artifactId>planner</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.ailog</groupId>
            <artifactId>executor</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.ailog</groupId>
            <artifactId>mcp-tool-center</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.ailog</groupId>
            <artifactId>dashboard</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create OrchestratorApplication.java**

```java
package com.ailog.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.ailog")
public class OrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrchestratorApplication.class, args);
    }
}
```

- [ ] **Step 3: Create WorkflowState.java**

```java
package com.ailog.orchestrator.state;

public enum WorkflowState {
    OBSERVE,
    CONTEXT,
    DIAGNOSE,
    PLAN,
    EXECUTE,
    VALIDATE,
    COMPLETED,
    FAILED
}
```

- [ ] **Step 4: Create StateMachine.java**

```java
package com.ailog.orchestrator.state;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class StateMachine {

    private WorkflowState currentState;

    private static final Map<WorkflowState, Set<WorkflowState>> transitions = new EnumMap<>(WorkflowState.class);

    static {
        transitions.put(WorkflowState.OBSERVE, Set.of(WorkflowState.CONTEXT, WorkflowState.FAILED));
        transitions.put(WorkflowState.CONTEXT, Set.of(WorkflowState.DIAGNOSE, WorkflowState.FAILED));
        transitions.put(WorkflowState.DIAGNOSE, Set.of(WorkflowState.PLAN, WorkflowState.FAILED));
        transitions.put(WorkflowState.PLAN, Set.of(WorkflowState.EXECUTE, WorkflowState.FAILED));
        transitions.put(WorkflowState.EXECUTE, Set.of(WorkflowState.VALIDATE, WorkflowState.FAILED));
        transitions.put(WorkflowState.VALIDATE, Set.of(WorkflowState.COMPLETED, WorkflowState.FAILED));
    }

    public StateMachine() {
        this.currentState = WorkflowState.OBSERVE;
    }

    public synchronized boolean transitionTo(WorkflowState target) {
        Set<WorkflowState> allowed = transitions.get(currentState);
        if (allowed != null && allowed.contains(target)) {
            currentState = target;
            return true;
        }
        return false;
    }

    public WorkflowState getCurrentState() {
        return currentState;
    }

    public boolean canTransitionTo(WorkflowState target) {
        Set<WorkflowState> allowed = transitions.get(currentState);
        return allowed != null && allowed.contains(target);
    }
}
```

- [ ] **Step 5: Create OrchestrationService.java**

```java
package com.ailog.orchestrator.service;

import com.ailog.common.event.Event;
import com.ailog.orchestrator.state.StateMachine;
import com.ailog.orchestrator.state.WorkflowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(OrchestrationService.class);

    private final StateMachine stateMachine;

    public OrchestrationService() {
        this.stateMachine = new StateMachine();
    }

    public void onEvent(Event event) {
        log.info("Received event: {} from {}", event.getType(), event.getSource());

        if (stateMachine.transitionTo(WorkflowState.CONTEXT)) {
            log.info("Transitioned to CONTEXT state");
        }
    }

    public WorkflowState getCurrentState() {
        return stateMachine.getCurrentState();
    }
}
```

- [ ] **Step 6: Create orchestrator/src/main/resources/application.yml**

```yaml
server:
  port: 8080

spring:
  application:
    name: ai-log-orchestrator
```

- [ ] **Step 7: Verify orchestrator compiles**

```bash
mvn clean compile -pl orchestrator -am -f "D:\Claude-Create-Project\ai-log-monitoring\pom.xml"
```

Expected: BUILD SUCCESS

---

### Task 5: 创建 dashboard 模块（REST API + WebSocket）

**Files:**
- Create: `dashboard/pom.xml`
- Create: `dashboard/src/main/java/com/ailog/dashboard/config/WebSocketConfig.java`
- Create: `dashboard/src/main/java/com/ailog/dashboard/controller/HealthController.java`
- Create: `dashboard/src/main/resources/application.yml`

- [ ] **Step 1: Create dashboard/pom.xml**

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

    <artifactId>dashboard</artifactId>
    <name>dashboard</name>
    <description>Dashboard backend API and WebSocket for frontend</description>

    <dependencies>
        <dependency>
            <groupId>com.ailog</groupId>
            <artifactId>common</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-websocket</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Create WebSocketConfig.java**

```java
package com.ailog.dashboard.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new EventWebSocketHandler(), "/ws/events")
                .setAllowedOrigins("*");
    }
}
```

- [ ] **Step 3: Create EventWebSocketHandler.java** (used by WebSocketConfig)

Create: `dashboard/src/main/java/com/ailog/dashboard/config/EventWebSocketHandler.java`

```java
package com.ailog.dashboard.config;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

public class EventWebSocketHandler extends TextWebSocketHandler {

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        session.sendMessage(new TextMessage("{\"status\": \"connected\"}"));
    }
}
```

- [ ] **Step 4: Create application.yml**

```yaml
dashboard:
  enabled: true
```

- [ ] **Step 5: Create HealthController.java**

```java
package com.ailog.dashboard.controller;

import com.ailog.common.model.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.success(Map.of(
                "status", "UP",
                "timestamp", Instant.now().toString()
        ));
    }
}
```

- [ ] **Step 6: Verify dashboard + orchestrator compile together**

```bash
mvn clean compile -pl orchestrator -am -f "D:\Claude-Create-Project\ai-log-monitoring\pom.xml"
```

Expected: BUILD SUCCESS

---

### Task 6: 安装前端依赖

**Files:**
- Modify: `ai-log-monitoring-ui/package.json` (add deps)

- [ ] **Step 1: Install frontend UI dependencies**

```bash
cd "D:\Claude-Create-Project\ai-log-monitoring\ai-log-monitoring-ui"
pnpm add @xyflow/react @monaco-editor/react cytoscape mermaid echarts echarts-for-react
pnpm add -D @types/cytoscape
```

Expected: All packages installed without errors

- [ ] **Step 2: Verify frontend builds**

```bash
cd "D:\Claude-Create-Project\ai-log-monitoring\ai-log-monitoring-ui"
pnpm run build
```

Expected: BUILD SUCCESS (or at least no errors, may have TS warnings)

---

### Task 7: 全量编译验证

- [ ] **Step 1: Full backend build**

```bash
mvn clean compile -f "D:\Claude-Create-Project\ai-log-monitoring\pom.xml"
```

Expected: BUILD SUCCESS

- [ ] **Step 2: Run orchestrator to verify startup**

```bash
mvn spring-boot:run -pl orchestrator -f "D:\Claude-Create-Project\ai-log-monitoring\pom.xml"
```

Expected: Spring Boot starts on port 8080, health endpoint works at `http://localhost:8080/api/health`
