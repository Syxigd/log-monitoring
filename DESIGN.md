# SE-Agent: AIOps 智能软件工程平台 — 完整设计文档

## 目录

1. [项目背景](#1-项目背景)
2. [总体架构](#2-总体架构)
3. [模块详解](#3-模块详解)
4. [完整数据流](#4-完整数据流)
5. [设计决策记录](#5-设计决策记录)
6. [边界与限制](#6-边界与限制)
7. [未来规划](#7-未来规划)

---

## 1. 项目背景

### 1.1 起源

现代软件系统依赖大量基础设施（Docker、MySQL、Redis、RocketMQ、对象存储等），故障排查面临三个根本困境：

- **信息分散**：日志、容器状态、数据库性能、消息队列堆积、代码变更分别在不同的工具中查看
- **上下文断裂**：收到告警后，需要手动从 5-6 个系统逐一查线索，再拼凑完整图景
- **分析经验化**：根因判断依赖工程师经验，同样的症状在不同团队可能有完全不同的排查路径

### 1.2 目标

构建一个 **AIOps 智能软件工程平台**（代号 SE-Agent），实现从日志监控到自动诊断、修复、验证、学习的完整软件工程 AI 闭环。

### 1.3 核心理念

- **事件驱动**：所有模块通过统一 Event 总线通信，天然解耦
- **确定性优先**：根因分析先用确定的规则链（Easy Rules），AI/LLM 作为后续增强
- **可操作**：不仅检测问题，还给出具体的修复建议、最终自动执行修复
- **增量构建**：15 个模块分阶段实现，每期交付可运行的闭环

### 1.4 环境信息

| 项目 | 值 |
|------|-----|
| 外部服务地址 | 192.168.152.128 |
| MySQL | 3306, database: alumni |
| Redis | 6379, authentication configured through `AILOG_REDIS_PASSWORD`, db: 1 |
| RocketMQ | 9876 |
| RustFS (S3) | 9001, bucket: alumni |

---

## 2. 总体架构

### 2.1 技术栈

**后端：** Java 21 + Spring Boot 3.4.4 + Spring AI 1.0.0-M6 + Maven

**前端：** React 19 + TypeScript + Vite + Tailwind 4 + shadcn

**关键库：** Easy Rules 4.1.0（规则引擎）、docker-java 3.4.1（Docker SDK）、AWS SDK S3（RustFS 兼容 S3）、RocketMQ Client 5.3.1

**MCP 框架：** spring-ai-mcp-server-webmvc（自动处理 JSON-RPC 2.0 + SSE）

### 2.2 模块全景（15 模块）

```
┌─────────────────────────────────────────────────────────────────┐
│                      orchestrator (唯一入口)                       │
│  @SpringBootApplication(scanBasePackages = "com.ailog")           │
│  状态机: OBSERVE→CONTEXT→DIAGNOSE→PLAN→EXECUTE→VALIDATE          │
└─────────────────────────────────────────────────────────────────┘
                                    │
         ┌──────────────────────────┼──────────────────────────┐
         ▼                          ▼                          ▼
┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐
│   observe-engine │    │  context-builder  │    │ root-cause-engine│
│  5 个 Collector   │───→│  7 个 Provider    │───→│  5 条 Easy Rule  │
│  30s 轮询发布 Event│    │  构建 ContextPackage│    │  两层分析链      │
└──────────────────┘    └──────────────────┘    └──────────────────┘
         │                                              │
         ▼                                              ▼
┌──────────────────────────────────────────────────────────────────┐
│                       mcp-tool-center                            │
│  5 个工具 × 22 方法: Docker/MySQL/Redis/RocketMQ/RustFS           │
│  @Service + @Tool 注解方法 + spring-ai MCP 自动注册                │
└──────────────────────────────────────────────────────────────────┘
         │
         ▼
┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐
│    dashboard     │    │     planner      │    │     executor     │
│  REST + WebSocket│    │  (骨架, 待实现)    │    │  (骨架, 待实现)    │
│  /api/health     │    │                   │    │                   │
│  /ws/events      │    │                   │    │                   │
└──────────────────┘    └──────────────────┘    └──────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│   空骨架模块 (待实现): code-knowledge-graph / validation-engine   │
│   review-agent / memory-engine / learning-engine                  │
└──────────────────────────────────────────────────────────────────┘
```

### 2.3 架构原则

| 原则 | 说明 | 原因 |
|------|------|------|
| **模块化单体** | 多模块 Maven 项目，orchestrator 是唯一 Spring Boot 入口 | 初期避免微服务分布式复杂度，同时保持模块边界清晰 |
| **包扫描统一** | `scanBasePackages = "com.ailog"`，所有模块的 Bean 被 orchestrator 发现 | 无需在每个模块添加 @SpringBootApplication |
| **依赖单向** | 每个模块依赖 common，不直接依赖其他业务模块 | 防止循环依赖，也防止模块间耦合过紧 |
| **事件总线解耦** | observe-engine → EventBus → 下游订阅者 | 发布者不需要知道谁在消费，天然异步可扩展 |
| **MCP 统一工具层** | 所有基础设施操作通过 mcp-tool-center，观测和分析层都通过它调用 | 统一的鉴权、错误处理、可观测性注入点 |
| **确定性 > AI** | 先用确定的规则链做根因分析，LLM 作为后续增强 | 确定性规则可测试、可解释、可调试；AI 辅助是后续能力 |

---

## 3. 模块详解

### 3.1 common — 共享模型

#### 功能
所有模块共用的基础类型定义。

#### 文件清单

| 文件 | 内容 |
|------|------|
| `Event.java` | 9 字段事件模型 |
| `EventType.java` | 12 种事件类型枚举 |
| `Severity.java` | 5 级严重程度枚举 |
| `EventBus.java` | 事件总线接口（含 subscribeAll） |
| `SimpleEventBus.java` | 内存版 EventBus 实现 |
| `Result.java` | 通用 API 响应包装 |

#### Event 模型（9 字段）

```java
字段: id, type, serviceName, exceptionType, errorMessage,
      time, source, severity, payload
```

**为什么 9 个字段？** 每个字段对应一个排查关键维度：
- `id` / `time` / `source` / `severity` — 事件的基础四要素（谁、何时、从哪来、多严重）
- `type` — 用于 EventBus 按类型分发订阅
- `serviceName` — 关联受影响的服务，用于聚合和影响范围评估
- `exceptionType` / `errorMessage` — 异常指纹，根因分析的核心匹配依据
- `payload` — 扩展容器，携带任何额外结构化数据（如容器 ID、SQL 语句等），保持 Event 核心精简的同时不限制扩展性

#### EventBus 设计

```
接口: publish(Event)
      subscribe(EventType, EventHandler)
      subscribeAll(EventHandler)   ← 第二期新增

实现: SimpleEventBus
      - 类型特定订阅: ConcurrentHashMap<EventType, CopyOnWriteArrayList<Handler>>
      - 全局订阅: CopyOnWriteArrayList<Handler>
      - 同步调用，异常吞噬
```

**为什么用简单内存实现而非 RocketMQ？** 第一期所有模块在同一个 JVM 进程内，内存事件总线零序列化开销、零网络延迟、零部署依赖。后续如果需要跨进程通信，可以增加 RocketMQ 实现但保留同一接口。

**为什么 CopyOnWriteArrayList？** 订阅配置在启动时固定，运行时几乎不修改。`publish` 是读多写极少场景，COW 不需要像读写锁那样协调，遍历时无锁。

#### EventType（12 种）

```
来源分类:
  日志: LOG_ERROR, LOG_WARN
  指标: METRIC_ALERT
  链路: TRACE_ANOMALY
  数据库: DB_SLOW_QUERY, REDIS_FAILURE
  消息: MQ_LAG
  容器: DOCKER_CRASH, K8S_EVENT
  部署: DEPLOY_EVENT
  CI/CD: CI_FAILURE
  配置: CONFIG_CHANGE
```

**为什么从具体来源命名而非抽象严重程度？** 事件类型不仅表达"发生了什么"，还携带"来自哪个观测维度"的信息，这决定了 context-builder 应该触发哪些 Provider 去采集上下文。

### 3.2 mcp-tool-center — 统一工具层

#### 功能
封装所有基础设施交互操作，对外暴露统一 MCP（Model Context Protocol）接口。既是人类排查的 CLI 替代品，也是 AI Agent 的工具集。

#### 架构决策

**为什么用 @Service + @Tool 注解而非传统 REST API？** MCP 协议（Model Context Protocol）是 AI Agent 的标准工具协议。`@Tool` 注解方法会被 spring-ai-mcp-server-webmvc 自动注册为 JSON-RPC 2.0 + SSE 端点，AI 模型可以直接发现和调用。

**为什么 5 个工具不合并？** 每个工具对应一个独立的外部服务，有独立的认证、连接管理和故障域。拆分后：一个工具不可用不影响其他工具；每个工具的语义空间独立（避免 AI 混淆 MySQL 和 Redis 的查询语法）。

#### 5 个工具详情

| 工具 | 方法数 | 核心能力 | 连接方式 | 为什么选这个 SDK |
|------|--------|----------|----------|----------------|
| **DockerTool** | 4 | ps / logs / restart / inspect | docker-java (Apache HTTP Client) | 官方 Java SDK，不需要额外安装 docker CLI |
| **MysqlTool** | 5 | query / tables / indexes / locks / slowSql | Spring JdbcTemplate | 复用 Spring Boot 的自动配置，零额外配置 |
| **RedisTool** | 5 | keys / ttl / memory / clusterInfo / monitor | StringRedisTemplate | 复用 Spring Boot 的自动配置 |
| **MqTool** | 4 | consumers / lag / retry / deadLetter | DefaultMQAdminExt | RocketMQ 官方管理客户端，每个方法独立实例（原因：DefaultMQAdminExt 不是线程安全的） |
| **RustfsTool** | 4 | list / get / put / delete | AWS SDK S3 v2 | RustFS 兼容 S3 API，AWS SDK 是最成熟的 S3 客户端 |

**为什么所有工具方法返回 String 而非结构化对象？** MCP 协议的工具返回值设计为人类可读文本（AI 也擅长解析文本），避免定义大量 DTO。同时简化了第一期的实现。

**已知缺陷：** MysqlTool.query() 存在 SQL 拼接风险（当前仅内部使用，但需后续修复）；MqTool 在每次调用时创建和销毁 DefaultMQAdminExt 实例，有性能改进空间。

### 3.3 observe-engine — 事件采集引擎

#### 功能
从 5 个数据源定期轮询，采集异常/事件，转换为标准 Event 发布到 EventBus。

#### 为什么用轮询而非 Watch/Push？
| 数据源 | 轮询原因 |
|--------|----------|
| 日志文件 | 文件系统的 WatchService 不可靠（特别是网络挂载卷和容器环境），RandomAccessFile + 位置追踪更健壮 |
| Docker | Docker 事件流是流式 API，但全量容器的状态快照只能轮询（ps()） |
| MySQL | MySQL 没有原生的慢查询推送机制，只能轮询 information_schema |
| Redis | INFO memory 是快照命令，无推送等价物 |
| MQ | RocketMQ 的消费进度需要主动查询 |

**30 秒间隔的依据？** 默认值，不频繁到影响外部服务性能，也不稀疏到错过重要事件。通过 `observe.collector.interval-ms` 可覆盖。

#### 5 个 Collector

| Collector | 触发条件 | 生成 EventType | 为什么这样判定 |
|-----------|----------|----------------|----------------|
| **LogCollector** | 日志文件新增行匹配 ERROR 正则 | LOG_ERROR | 最基础的异常信号源，ERROR 级别通常对应需要处理的问题 |
| **DockerCollector** | 容器状态变为 exited / unhealthy | DOCKER_CRASH | 容器退出是最直接的故障信号，差异化检测避免重复告警 |
| **MysqlCollector** | slowSql() 返回非空 | DB_SLOW_QUERY | 慢查询是数据库性能问题的最常见指标，执行时间 > 1 秒值得关注 |
| **RedisCollector** | memory() 包含 used_memory_rss | REDIS_FAILURE | used_memory_rss 是实际物理内存，超过配置上限会导致 OOM |
| **MqCollector** | retry() 返回正数值 | MQ_LAG | 重试队列非零表示消费者处理速度跟不上生产速度 |

**为什么 LogCollector 用 RandomAccessFile 而非 WatchService？** WatchService 在容器化环境中存在已知问题（文件不可达、事件丢失），RandomAccessFile 配合文件位置持久化更可靠。每个文件追踪已读位置，只处理新增行，避免重复发送。

**为什么代码里直接 new Event() 而非工厂？** 第一期简化实现，每个 Event 字段都显式构造。后续可考虑 Builder 模式。

**DockerCollector 的状态追踪如何工作？** `previousStates` 是一个本地 Map，存储上次轮询时每个容器的状态。只有状态从 running 变为 exited 时才生成事件，避免每次轮询重复告警。

### 3.4 context-builder — 上下文构建引擎

#### 功能
收到 Event 后，从 7 个数据源采集当前快照，组装成 ContextPackage 供根因分析使用。

#### 为什么单独一个模块而不是合并到根因分析？
分离关注点：context-builder 只负责"采集和组织数据"，不负责"分析和推理"。这使得：
- 每个 Provider 可以独立测试（mock Event，验证输出）
- 新增数据源只需要加 Provider，不改分析逻辑
- 上下文可以缓存/复用（同类型事件共享上下文）

#### 7 个 Provider

| Provider | 数据来源 | 采集什么 | 为什么重要 |
|----------|----------|----------|-----------|
| **DockerContextProvider** | DockerTool | 所有容器列表 + 异常容器的最近 50 行日志 | 容器崩溃现场证据 |
| **DbContextProvider** | MysqlTool | 当前慢查询 + 锁等待 | 数据库是大部分性能问题的根源 |
| **RedisContextProvider** | RedisTool | 内存使用 + 集群信息 | Redis 内存耗尽会导致连锁故障 |
| **MqContextProvider** | MqTool | 消费延迟 + 重试队列 | 消息积压暴露下游处理瓶颈 |
| **GitContextProvider** | Git CLI | 最近 10 条提交 + 最近变更文件 | 代码变更是最常被忽略的根因 |
| **CodeContextProvider** | 文件系统 | 根据错误信息提取类名并读取源码 | 直接看到出错的代码行 |
| **EnvContextProvider** | JVM 系统属性 | Java 版本、OS、CPU、内存 | 环境差异常导致"在我机器上能跑"问题 |

**为什么 Git 和 Code Provider 不用 MCP 工具？** Git 操作和文件读取是纯本地操作，不需要经过 MCP 网络层。直接使用 Runtime.exec() 和 Files API 更快、更简单。

**为什么 Provider 失败不影响整体？** 每个 Provider 的 collect() 被 try-catch 包裹，失败时返回 ContextData.failure()。下游分析层检查 isSuccess() 来决定是否使用该数据。这种设计确保一个数据源不可用不会阻断整个分析链。

#### 失败隔离的设计意图

```java
// ContextBuilderService.build()
for (Provider provider : providers) {
    try { pkg.addContext(provider.collect(event)); }
    catch (Exception e) { pkg.addContext(ContextData.failure(...)); }
}
// 永远返回 ContextPackage，没有短路
```

分析层应检查 `contextData.isSuccess()`，对失败的数据源降级处理而非直接放弃分析。例如 GitContextProvider 失败时，CodeDefectRule 应判定为"证据不足以判定代码缺陷"而非报错。

### 3.5 root-cause-engine — 根因分析引擎

#### 功能
接收 ContextPackage，通过 5 条确定性规则匹配，输出结构化 RootCauseReport。

#### 为什么选 Easy Rules 而非自研规则引擎？

| 维度 | Easy Rules | 自研 |
|------|-----------|------|
| 学习成本 | 1 小时 | 需要设计规则 DSL 和引擎 |
| 灵活性 | 每条规则是独立 POJO | 可定制但开发量大 |
| 兼容性 | 4.1.0 稳定版，无依赖冲突 | 需要自己管理版本 |
| 集成复杂度 | 5 行代码注册一条规则 | 需要自己实现编排 |

Easy Rules 的核心模式（evaluate/execute 分离 + 优先级排序）天然匹配我们的"逐层匹配 → 证据组合"场景。

#### 两层分析链设计

```
Layer 1（直接根因，优先级 1-3）:
  CodeDefectRule       (1) — exceptionType 非空 + Git 有最近提交
  ResourceExhaustionRule (2) — Docker OOM / Redis 内存告警
  ConfigErrorRule      (3) — errorMessage 含配置关键词
  
  → 任一命中 → 直接返回，不再继续

Layer 2（影响/症状，优先级 4-5）:
  DbPerformanceRule    (4) — DB_SLOW_QUERY 事件 + 慢 SQL 详情
  DependencyFailureRule (5) — MQ_LAG 事件 + 消费堆积
  
  → 全部运行，取证据链最长的返回
```

**为什么这样分层？** 经过对真实故障案例的分析，故障模式可分为两类：
1. **有明确根因信号的**（异常类型 + 代码变更、OOM、配置错误）— 应该直接判定，不需要更多证据，因为再多的证据也不会改变根因结论。
2. **只有症状信号的**（慢查询、消息堆积）— 它们是问题的表现而非原因，多条症状同时出现时，证据链最长的那个最可能是根因。

**为什么 Layer 1 优先于 Layer 2？** 如果同时存在代码变更和消息堆积，代码变更更可能是根因（代码导致服务变慢 → MQ 堆积），先处理根本原因而非症状。

#### 5 条规则的证据逻辑

| 规则 | evaluate() 条件 | 为什么这样判定 |
|------|----------------|----------------|
| **CodeDefectRule** | exceptionType 非空 + GitContext 成功且含最近提交 | 异常 + 代码变更 → 变更引入了 bug。如果 Git 失败（比如不在 Git 仓库中），则证据不足，不判定 |
| **ResourceExhaustionRule** | Docker 日志含 OOM/kill / Redis 内存含 used_memory_rss | 两个条件满足任意一个即可判定。不要求同时满足是因为资源耗尽通常只在一个组件上表现出来 |
| **ConfigErrorRule** | errorMessage 含 config/FileNotFound 等关键词 | 纯文本匹配，不依赖上下文数据。适合快速判定，但可能有误报 |
| **DbPerformanceRule** | EventType=DB_SLOW_QUERY + DbContext 含慢 SQL | 双重确认：既有慢查询事件，上下文也确认了慢查询详情，避免误报 |
| **DependencyFailureRule** | EventType=MQ_LAG + MqContext 含延迟数据 | 同理，事件 + 上下文双重确认消费堆积 |

#### 第二期改进（已完成）

在 2026-07-16 完成了 4 项改进：

**1. 类型安全（ContextData）**
- 新增 11 个 Key 常量（`GIT_COMMITS`、`DOCKER_LOGS` 等）
- 新增 7 个 Provider 名称常量（`PROVIDER_GIT`、`PROVIDER_DOCKER` 等）
- 新增 `get(key, Class<T>)` 类型安全访问器，消除 `(String) map.get(...)` 强转

**为什么改？** 第一期使用 `map.get("magicString")`，字符串散落在代码各个位置，改一个 key 名要 grep 全项目。类型安全访问器在 key 不存在时返回 null 而非抛出 ClassCastException。

**2. EventBus 自动触发**
- RootCauseEngineService 通过 `@PostConstruct` + `subscribeAll()` 监听所有 Event
- 收到事件 → `ContextBuilderService.build(event)` → `analyze(context)`
- EventBus 新增 `subscribeAll()` 方法

**为什么之前不这样做？** 第一期 context-builder 和 root-cause-engine 是分别独立构建的，两阶段之间存在"先有鸡还是先有蛋"的问题。第一期完成后再串联是最合理的时机。

**3. 规则可配置化**
- 新建 `RuleConfigProperties` (`@ConfigurationProperties(prefix = "rootcause")`)
- 支持在 application.yml 中配置每条规则的 `enabled` 和 `priority`
- OrchestratorApplication 添加 `@ConfigurationPropertiesScan("com.ailog")`

**为什么做？** 规则在生产环境需要调整：新环境可能暂时关闭某些规则、紧急情况需要跳过不太准确的规则、或者需要临时调整优先级。硬编码意味着改代码重新部署。

**4. SimpleEventBus.subscribeAll()**
- 新增全局订阅机制，监听所有 EventType 的事件
- 发布时先通知类型特定监听器，再通知全局监听器

**为什么需要 subscribeAll 而非逐个 subscribe？** RootCauseEngineService 需要处理所有事件类型的分析，逐一 subscribe 12 种 EventType 是重复代码，且后续新增 EventType 时容易遗漏。

### 3.6 orchestrator — 状态机编排器

#### 功能
管理整体工作流状态流转，当前为骨架实现。

#### 状态机（8 状态）

```
OBSERVE → CONTEXT → DIAGNOSE → PLAN → EXECUTE → VALIDATE → COMPLETED
   ↓        ↓         ↓         ↓        ↓          ↓
                → FAILED（所有状态均可到达）
```

**为什么用状态机？** AI 自动修复流程不能跳过任何步骤（比如没采集上下文就做根因分析），状态机强制执行正确顺序。同时每个状态的失败处理路径明确。

**为什么当前是骨架？** 前三个模块（observe/context/root-cause）刚完成，状态机需要等后续模块（planner/executor/validation）完成后才能真正串联。

### 3.7 dashboard — 后端 API

#### 功能
提供 REST API + WebSocket 端点，前端通过它们获取数据和接收实时推送。

#### 当前端点
- `GET /api/health` — 健康检查，返回 `{status: "UP", timestamp: "..."}`
- `WebSocket /ws/events` — 事件推送通道，当前仅回复 `{status: "connected"}`

**为什么只有健康检查？** 前端页面骨架刚搭建，等页面开发启动后按需扩展 API。

### 3.8 前端（ai-log-monitoring-ui）

#### 技术选型

| 选型 | 版本 | 为什么选 |
|------|------|----------|
| React 19 | 19.2.6 | 最新稳定版，并发特性 + Server Components 支持 |
| Vite 8 | 8.x | 极快的 HMR，TypeScript 原生支持 |
| Tailwind 4 | 4.x | CSS 零运行时，编译时生成，v4 的 `@tailwindcss/vite` 插件零配置 |
| shadcn | — | 非组件库而是代码生成器，提供可定制的组件源码 |
| React Flow | 12.11.2 | 工作流/状态机可视化，支持节点拖拽和自定义 |
| Cytoscape.js | 3.34.0 | 知识图谱可视化，性能优于 D3.js |
| ECharts | 6.1.0 | 监控大盘的时间序列图表 |
| Mermaid | 11.16.0 | 动态流程图渲染 |

**为什么选择 React Flow 3 而非 XYFlow？** React Flow 12 就是 @xyflow/react 品牌下的最新版，已包含所有前端需要的节点/边/布局功能。

**为什么同时安装 Cytoscape 和 Mermaid？** 用途不同：Cytoscape 用于交互式图谱探索（知识图谱的节点/关系图），Mermaid 用于嵌入在报告中的静态流程图渲染。

---

## 4. 完整数据流

### 4.1 核心闭环

```
30s 定时器
  ↓
ObserveScheduler.runCollectors()
  ↓ 遍历所有 Collector
LogCollector.collect()          ← 扫描日志文件（RandomAccessFile）
DockerCollector.collect()        ← 轮询容器状态（DockerTool）
MysqlCollector.collect()         ← 检查慢查询（MysqlTool）
RedisCollector.collect()         ← 检查内存（RedisTool）
MqCollector.collect()            ← 检查消费堆积（MqTool）
  ↓ 每个 Collector 返回 List<Event>
ObserveEngineService.collectAll()
  ├── EventBus.publish(event)    ← 推送到事件总线
  ↓
SimpleEventBus.publish()
  ├── 类型匹配的 EventHandler     ← 按 EventType 分发
  └── subscribeAll 的 EventHandler ← 全局监听
        ↓
RootCauseEngineService.onEvent()
  └── ContextBuilderService.build(event)
        ├── DockerContextProvider.collect()
        ├── DbContextProvider.collect()
        ├── RedisContextProvider.collect()
        ├── MqContextProvider.collect()
        ├── GitContextProvider.collect()
        ├── CodeContextProvider.collect()
        └── EnvContextProvider.collect()
        ↓
      ContextPackage (含 7 个 ContextData)
        ↓
  RootCauseEngineService.analyze(contextPackage)
        ├── CodeDefectRule.evaluate()       → execute()
        ├── ResourceExhaustionRule.evaluate() → execute()
        ├── ConfigErrorRule.evaluate()       → execute()
        ├── DbPerformanceRule.evaluate()     → execute()
        └── DependencyFailureRule.evaluate() → execute()
        ↓
      RootCauseReport (根因 + 证据链 + 建议操作)
        ↓
      log.info("Auto-analysis complete: CODE_DEFECT for event xxx")
        ↓
  ─→ (后续阶段) planner 接收报告 → executor 执行修复 → validation 验证
```

### 4.2 容错路径

```
任何 Collector 失败 → log.error + 跳过该 Collector，不影响其他
任何 Provider 失败 → ContextData.failure() 携带错误信息，不影响其他
任何 Rule 失败 → log.warn + 跳过该 Rule，继续其他
EventBus 的 handler 异常 → 空 catch 块吞噬，不影响其他订阅者
```

---

## 5. 设计决策记录

### 决策 1：模块化单体 vs 微服务

**问题：** 15 个模块应该以什么方式组织？

**方案权衡：**
- 微服务：独立部署、独立扩展、独立开发 → 但引入服务发现、RPC、分布式事务、CI/CD 复杂度
- 模块化单体：Maven 多模块，共享同一个 JVM → 无网络开销、无序列化、调试直观

**选择：** 模块化单体。

**原因：** 项目处于 0→1 阶段，连核心流程都没跑通，微服务的复杂度会拖慢开发速度。模块在代码层面已经分开（各自有 pom.xml 和包路径），后续需要拆微服务时只是把模块提成独立项目的问题。

### 决策 2：EventBus 同步 vs 异步

**问题：** 事件发布应该是同步还是异步？

**选择：** 同步（第一期）。

**原因：** 当前所有模块在同一个进程内，同步调用零延迟、零丢消息、不需要管理线程池。如果后续需要异步（比如通知前端 WebSocket），可以在 EventHandler 内部自行提交线程池，不改变 EventBus 接口。

### 决策 3：规则引擎 vs 流程编排

**问题：** 根因分析应该用规则引擎还是硬编码 if-else？

**选择：** Easy Rules 规则引擎。

**原因：** if-else 链：
- 新增规则需要修改 if-else 结构
- 无法在不重启的情况下调整规则优先级
- 规则之间的逻辑关系不够直观

Easy Rules 的 POJO 规则模式：
- 新增规则只需要新建类实现接口
- 优先级通过 `getPriority()` 返回
- 第二期还加了 YAML 配置的 enabled/priority 覆盖

### 决策 4：类型安全方案

**问题：** ContextData 的 data Map 如何保证类型安全？

**方案权衡：**
- 强类型 DTO：为每个 Provider 创建独立的 XxxContextData 类 → 类爆炸
- 泛型访问器：`<T> T get(key, Class<T>)` → 运行时仍有类型擦除但调用点有类型断言
- Status quo：`(String) map.get(key)` → 无编译检查

**选择：** 泛型访问器 + Key 常量。

**原因：** 强类型 DTO 产生 7 个一对一类，每个只有 2-3 个字段，过度工程。泛型访问器提供了调用点的类型检查（IDE 的自动推导），Key 常量提供了 IDE 自动补全和引用搜索。运行时如果类型不匹配，`Class.cast()` 会抛出 ClassCastException 而非静默类型混淆。

### 决策 5：异常处理策略

**问题：** 各层应该如何处理异常？

**策略：**
- **采集层（Collector/Provider）：** 捕获所有异常，返回空结果或 failure 状态。绝不抛出。
- **分析层（Engine/Service）：** 捕获每个规则的异常，跳过失败的规则继续其他规则。绝不中断。
- **EventBus 发布：** 捕获每个 handler 的异常，不影响其他 handler。绝不中断。

**原因：** 在一个 AIOps 平台中，"部分数据不可用"比"完全不输出"好得多。如果一个 Provider 失败就中断整个分析链，系统在上线初期会因为各种环境问题频繁不可用。

### 决策 6：中文提示和文档

**问题：** 代码中的中文信息（建议操作）是否合适？

**选择：** 产品面向中国 DevOps 团队，故障响应场景看英文建议反而有认知负担。规则中的 `suggestedAction` 用中文（如"检查资源限制并扩容，或排查内存泄漏"）直接给可读的修复指引。

### 决策 7：MCP 协议选择

**问题：** 工具层用 REST API 还是 MCP 协议？

**选择：** MCP（Model Context Protocol）。

**原因：** 项目目标是 AI 驱动的自动修复，MCP 是 AI Agent 调用外部工具的标准协议。`@Tool` 注解 + `spring-ai-mcp-server-webmvc` 自动注册，AI 模型可以直接发现和调用所有工具方法。REST API 需要 AI 自己去构建 HTTP 请求和解析响应，MCP 标准化的 JSON-RPC 2.0 格式减少了集成成本。

---

## 6. 边界与限制

### 6.1 已完成的模块

| 模块 | 状态 | 验证 |
|------|------|------|
| common | 完成 | 6 个类，含 Event/EventBus/Result |
| mcp-tool-center | 完成 | 5 个工具，22 个 @Tool 方法 |
| observe-engine | 完成 | 5 个 Collector + Scheduler + Service |
| context-builder | 完成 | 7 个 Provider + Service + 模型 |
| root-cause-engine | 完成（两期） | 5 条规则 + 配置化 + EventBus 接入 |
| dashboard | 骨架 | 健康检查 + WebSocket 配置 |
| orchestrator | 骨架 | 状态机（8 状态） |

### 6.2 已知限制

| 限制 | 影响 | 计划修复时机 |
|------|------|-------------|
| observe-engine 只有 5 个 Collector，缺 LOG_WARN/METRIC_ALERT 等 7 种 EventType 的采集实现 | 即使这些 EventType 已定义，也没有对应的事件采集 | 后续阶段补充 |
| LogCollector 路径依赖配置项 `observe.collector.log.path`，未配置则静默跳过 | 用户忘记配置时日志监听不会工作 | 第一期已标注为已知限制 |
| Collector 无健康检查和熔断机制 | 单个 Collector 连续失败时无告警 | 后续阶段补充 |
| ContextBuilderService 尚未订阅 EventBus（当前被 RootCauseEngineService 调用触发） | 上下文构建不会独立自动触发 | 第二期改为：RootCauseEngineService 调用 contextBuilder.build()（已实现） |
| CodeContextProvider 用简单类名正则匹配，未实现真实堆栈解析 | 只能匹配简单的 `Xxx.java` 模式，复杂堆栈提取能力有限 | 后续阶段改进 |
| 缺少 Trace 链路数据 | 无法关联分布式追踪中的跨服务调用链 | 配合 OpenTelemetry 实现 |
| MysqlTool.query() 存在 SQL 拼接 | SQL 注入风险（当前仅内部使用） | 后续改为 PreparedStatement |
| MqTool 每个方法创建和销毁 DefaultMQAdminExt | 性能开销，连接管理冗余 | 抽取共享客户端到 @Bean |
| root-cause-engine 未接入实际 EventBus 事件流（已修复） | 需要显式调用 analyze() | 第二期已完成修复 |
| 规则是硬编码关键词匹配 | 可扩展性有限，无法覆盖所有场景 | 后续引入 LLM 辅助分析 |
| 缺少多事件关联分析（第二期改进方案已设计未实现完整 EventGroup） | 无法聚合同一事件的多条证据 | 第二期改进方案已设计 |
| 状态机是骨架，未与模块连接 | 状态不会实际流转 | 等 planner/executor 模块完成后连接 |
| 前端页面未开发 | 无可视化界面 | 待启动 |
| 无测试覆盖 observe-engine 和 context-builder | 只测试了 root-cause-engine | 后续补充 |
| Spring AI 1.0.0-M6（里程碑版本） | 非正式发布版，可能存在接口变动 | 待正式版发布后升级 |

### 6.3 外部服务依赖关系

所有外部服务运行在 `192.168.152.128`。当前开发环境是 Windows，外部服务可能需要独立搭建或使用 Docker 运行在 Linux VM 中。建议的环境准备：

```
1. Docker: 需要 Docker daemon 运行中
2. MySQL: 3306 端口可访问，已创建 alumni 数据库
3. Redis: 6379 端口可访问，凭据由 `AILOG_REDIS_PASSWORD` 注入
4. RocketMQ: 9876 端口可访问（nameserver）
5. RustFS (MinIO 兼容): 9001 端口可访问，已创建 alumni bucket
```

---

## 7. 未来规划

### 7.1 模块实现优先级

```
第一期（已完成）:
  common → mcp-tool-center → observe-engine → context-builder → root-cause-engine

第二期（部分完成）:
  root-cause-engine 改进（类型安全 / EventBus 接入 / 配置化）✓
  → 多事件关联分析（EventGroup / EventCorrelationEngine）

第三期（待启动）:
  前端页面开发（仪表盘 / 事件中心 / 根因分析 / 配置）
  → planner 模块（修复计划生成）
  → executor 模块（自动修复执行）

第四期（待启动）:
  validation-engine（修复验证）
  → review-agent（代码审查）
  → memory-engine（经验记忆）
  → learning-engine（持续学习）
  → code-knowledge-graph（代码知识图谱）
```

### 7.2 架构演进路径

```
第一阶段: 模块化单体（当前）
  - 所有模块在同一 JVM
  - EventBus 内存实现
  - 简化部署

第二阶段: 事件总线升级
  - RocketMQ EventBus 实现
  - 跨进程事件通信
  - 事件持久化和回放

第三阶段: 微服务拆分（按需）
  - observe-engine 独立部署
  - root-cause-engine 独立部署
  - 前端 + dashboard 独立部署
```

### 7.3 关键技术预研

- **LLM 辅助根因分析**：将 ContextPackage + Rule 输出作为 prompt 输入，让 LLM 做更深层的根因推测
- **规则自学习**：learning-engine 根据历史修复结果自动调整规则阈值和权重
- **知识图谱联动**：code-knowledge-graph 提供代码调用关系，辅助根因影响范围评估
- **OpenTelemetry 集成**：Trace 数据补充上下文，实现跨服务根因追踪

---

> 本文档最后更新：2026-07-16
> 对应代码版本：项目根目录唯一 `.git`，共 7 个 commit，模块按提交顺序：infrastructure → observe-engine → context-builder → root-cause-engine → 第二期改进
