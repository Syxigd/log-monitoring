# Context Builder — 第一期设计

## 目标

收到 Event 后，聚合日志、Git、Docker、数据库、Redis、MQ、代码、配置等多源信息，形成完整的 ContextPackage 供下游分析。

## 输入与输出

- **输入:** `Event`（来自 observe-engine）
- **输出:** `ContextPackage` — 包含所有 Provider 的采集结果

## 架构

```
Event → ContextBuilderService
         ├── DockerContextProvider  → DockerTool
         ├── DbContextProvider      → MysqlTool
         ├── RedisContextProvider   → RedisTool
         ├── MqContextProvider      → MqTool
         ├── GitContextProvider     → Git CLI 调用
         ├── CodeContextProvider    → 文件系统读取
         └── EnvContextProvider     → JVM/OS 系统信息
         ↓
      ContextPackage
```

## 组件

### ContextProvider 接口

```java
public interface ContextProvider {
    String name();
    ContextData collect(Event event);
}
```

`ContextData` 包含 `providerName`, `data(Map)`, `success(boolean)`, `errorMessage`。

### ContextPackage

```java
public class ContextPackage {
    private String eventId;
    private Event originalEvent;
    private Map<String, ContextData> contexts;
    private Instant collectedAt;
}
```

### 7 个 Provider

| Provider | 依赖 | 采集内容 |
|----------|------|----------|
| DockerContextProvider | DockerTool | 检测异常容器、获取日志尾部 |
| DbContextProvider | MysqlTool | 慢 SQL、当前锁 |
| RedisContextProvider | RedisTool | 内存使用 |
| MqContextProvider | MqTool | 消费延迟、重试队列 |
| GitContextProvider | Git CLI | 最近 commit、文件变更、作者 |
| CodeContextProvider | 文件系统 | 根据堆栈/关键行读取源码片段 |
| EnvContextProvider | 系统属性 | JVM 参数、OS、部署环境 |

### ContextBuilderService

- 注入所有 `ContextProvider` Bean
- `build(Event) → ContextPackage`
- 每个 Provider 独立执行，失败不影响其他

## 非本期范围

Trace 链路数据（后续配合 OpenTelemetry 实现）。
