# Root Cause Engine — 第二期改进设计

## 目标

解决第一期 4 个待办项：自动触发、多事件关联、类型安全、规则可配置。

## 四大改进

### 1. 类型安全（common 模块）

**ContextData 新增 Key 常量和类型安全访问器：**

```java
public class ContextData {
    // 类型安全 Key 常量
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

    public <T> T get(String key, Class<T> type) {
        Object value = data.get(key);
        if (value == null) return null;
        return type.cast(value);
    }
}
```

**受影响的 Provider（全部均需改用常量）：**
- DockerContextProvider: `data.put(ContextData.DOCKER_LOGS, ...)`
- GitContextProvider: `data.put(ContextData.GIT_COMMITS, ...)`
- 各 Rule 读取处: `gitData.get(ContextData.GIT_COMMITS, String.class)`

### 2. EventBus 接入（root-cause-engine）

新增 `RootCauseEventListener`，订阅 EventBus 后自动触发：

- 订阅 5 种 EventType: LOG_ERROR, DB_SLOW_QUERY, DOCKER_CRASH, REDIS_FAILURE, MQ_LAG
- 收到事件 → `contextBuilder.build(event)` → `correlator.feed(pkg)`
- 异常不影响后续事件（try-catch 包裹）

### 3. 多事件关联（root-cause-engine）

新增 `EventCorrelationEngine` + `EventGroup`，三层关联匹配：

**第一层（异常指纹）：**
- 根据 `exceptionType + errorMessage` 前 50 字构建指纹
- 相同指纹的事件自动归组
- 窗口 5 分钟，窗口到期后触发分析

**第二层（同服务窗口）：**
- 第一层未匹配 → 按 `serviceName + source` 聚合
- 窗口 5 分钟，收集窗口内所有事件

**第三层（跨服务规则）：**
- 定期扫描过期 EventGroup，应用预定义关联规则
- 已有规则：`MQ_LAG + DB_SLOW_QUERY` → 数据库性能问题
- 其他规则留待数据验证后补充

**EventGroup.merge()：**
- 保留第一条事件的主信息
- 证据链合并所有事件的 ContextData
- 时间取最早事件的时间

**RootCauseEngineService.analyze() 改为接收 EventGroup：**
- 输入 EventGroup → merge() → 原有两层规则链不变

### 4. 规则可配置（root-cause-engine）

将规则匹配条件抽到 application.yml：

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

**受影响的 Rule：**
- `ConfigErrorRule`: 关键词改为从 `@Value` 注入
- `ResourceExhaustionRule`: OOM 关键词改为从 `@Value` 注入
- 其他 Rule 暂不调整

## 数据流（改进后）

```
EventBus → RootCauseEventListener
              ↓ contextBuilder.build(event)
           ContextPackage
              ↓
           EventCorrelationEngine
              ├── FingerprintMatcher   (第一层)
              ├── ServiceWindowMatcher (第二层)
              └── CrossServiceScanner  (第三层，定时)
              ↓ EventGroup
           RootCauseEngineService.analyze()
              ↓ 两层规则链
           RootCauseReport
```

## 非本期范围

- LLM 辅助分析（待后续版本）
- 规则动态加载/热更新
- 关联规则的数据驱动发现
- 事件关联结果持久化

## 改动文件清单

| 文件 | 模块 | 操作 |
|------|------|------|
| `ContextData.java` | common | 修改 — 添加 Key 常量和 get(Class) 方法 |
| `DockerContextProvider.java` | context-builder | 修改 — 用常量替换硬编码 key |
| `GitContextProvider.java` | context-builder | 修改 — 用常量替换硬编码 key |
| `DbContextProvider.java` | context-builder | 修改 — 用常量替换硬编码 key |
| `RedisContextProvider.java` | context-builder | 修改 — 用常量替换硬编码 key |
| `MqContextProvider.java` | context-builder | 修改 — 用常量替换硬编码 key |
| `EnvContextProvider.java` | context-builder | 修改 — 用常量替换硬编码 key |
| `RootCauseEventListener.java` | root-cause-engine | 新增 |
| `EventCorrelationEngine.java` | root-cause-engine | 新增 |
| `EventGroup.java` | root-cause-engine | 新增 |
| `CodeDefectRule.java` | root-cause-engine | 修改 — 用 ContextData.get(Class) |
| `ResourceExhaustionRule.java` | root-cause-engine | 修改 — 用 ContextData.get(Class) + 配置注入 |
| `ConfigErrorRule.java` | root-cause-engine | 修改 — 用 ContextData.get(Class) + 配置注入 |
| `DbPerformanceRule.java` | root-cause-engine | 修改 — 用 ContextData.get(Class) |
| `DependencyFailureRule.java` | root-cause-engine | 修改 — 用 ContextData.get(Class) |
| `RootCauseEngineService.java` | root-cause-engine | 修改 — 接收 EventGroup |
| `RootCauseEngineServiceTest.java` | root-cause-engine | 修改 — 适配新签名 |
| `application.yml` | orchestrator | 修改 — 添加规则配置 |
