# Observe Engine — 第一期设计

## 目标

监听多源事件并统一转换为 Event 发布到事件总线，供下游模块消费。

## 范围（第一期，5 个事件源）

日志 / Docker / MySQL / Redis / RocketMQ

## 架构

```
LogCollector ─┐
DockerCollector ─┤                  ┌──────────────┐
MysqlCollector ─┼─→ ObserveScheduler ──→ EventBus.publish()
RedisCollector ─┤    (30s 轮询)      └──────────────┘
MqCollector ────┘
```

## 组件

### EventCollector 接口

```java
public interface EventCollector {
    String name();
    List<Event> collect();
}
```

### 5 个 Collector

| Collector | 采集逻辑 | 复用 mcp 工具 |
|-----------|---------|--------------|
| `LogCollector` | WatchService 监听日志文件，正则匹配 ERROR，生成 LogEvent | 否 |
| `DockerCollector` | 轮询 `DockerTool.ps()`，检测异常容器状态 | DockerTool |
| `MysqlCollector` | 轮询 `MysqlTool.locks()` / `MysqlTool.slowSql()` | MysqlTool |
| `RedisCollector` | 轮询 `RedisTool.memory()`，检测内存异常 | RedisTool |
| `MqCollector` | 轮询 `MqTool.lag()` / `MqTool.deadLetter()`，检测消费堆积 | MqTool |

### ObserveScheduler

- `@Scheduled` 定时调用所有 Collector
- 每个 Collector 返回的 Event 通过 EventBus.publish() 发布
- 配置项：`observe.collector.log.path`, `observe.collector.interval-ms`

## 与 Prometheus 的分工

- 本期做 mcp-tool-center 轮询方案（可执行操作，适合排查）
- 后续 Prometheus + AlertManager 部署后，通过 Webhook 补充告警入口（适合持续监控）
- 两者独立，不冲突

## 非本期范围

Trace、Git、服务器、对象存储、网络、JVM 等事件源——后续阶段实现。
