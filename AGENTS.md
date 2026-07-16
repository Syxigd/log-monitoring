# AI Log Monitoring — Agent 指令

## 项目概述

AIOps 软件工程智能体平台。Maven 多模块项目（15 模块），模块化单体架构。

## 构建

```bash
# 全量编译
mvn clean compile -f pom.xml

# 运行
mvn spring-boot:run -pl orchestrator

# 前端
cd ai-log-monitoring-ui && pnpm dev && pnpm build
```

**已知问题：** JDK 21 在此环境不支持 `--release 21`。parent POM 已通过 `maven.compiler.source/target` 绕开，勿改回 `<java.version>` 默认行为。Spring AI 1.0.0-M6 需要 Spring Milestone 仓库（已配置）。

## 架构

- **orchestrator**（`com.ailog.orchestrator`）是唯一 Spring Boot 入口，`@SpringBootApplication(scanBasePackages = "com.ailog")` 扫描所有模块
- 其他模块是纯库模块（无 main class，无 spring-boot-maven-plugin），通过 orchestrator 的依赖引入
- 所有模块包前缀 `com.ailog.<module>`
- 连接配置集中管理：`orchestrator/src/main/resources/application.yml`

## 模块

| 模块 | 状态 | 包 |
|------|------|-----|
| common | 完成 — Event/EventBus/Result/SimpleEventBus | com.ailog.common |
| mcp-tool-center | 完成 — 5 工具集 Docker/MySQL/Redis/RocketMQ/RustFS，标准 MCP | com.ailog.mcp |
| observe-engine | 完成 — 5 Collector(Log/Docker/MySQL/Redis/MQ) + ObserveEngineService + ObserveScheduler，30s 轮询发布至 EventBus | com.ailog.observe |
| context-builder | 完成 — ContextProvider 接口 + 7 Provider(Docker/DB/Redis/MQ/Git/Code/Env) + ContextBuilderService + ContextPackage | com.ailog.context |
| code-knowledge-graph | 空骨架 | com.ailog.knowledge |
| root-cause-engine | 完成 — RootCause 枚举 + RootCauseReport 模型 + RootCauseAnalyzer 接口 + 5 个 Easy Rule(CodeDefect/ResourceExhaustion/ConfigError/DbPerformance/DependencyFailure) + RootCauseEngineService 两层编排 | com.ailog.rootcause |
| planner | 空骨架 | com.ailog.planner |
| executor | 空骨架 | com.ailog.executor |
| validation-engine | 空骨架 | com.ailog.validation |
| review-agent | 空骨架 | com.ailog.review |
| memory-engine | 空骨架 | com.ailog.memory |
| learning-engine | 空骨架 | com.ailog.learning |
| dashboard | REST + WebSocket 基础 | com.ailog.dashboard |
| orchestrator | 状态机骨架（8 状态） | com.ailog.orchestrator |

## MCP 工具开发约定

- 工具类：`@Service` + `@Tool(description="...")` 注解方法
- 自动注册：Spring AI 的 `spring-ai-mcp-server-webmvc` 自动处理 JSON-RPC 2.0 + SSE
- 新工具类放在 `mcp-tool-center/src/main/java/com/ailog/mcp/`

## 外部服务

全部运行在 `192.168.152.128`：
- MySQL 3306 / Redis 6379 / RocketMQ 9876 / RustFS(S3) 9001
- 凭据见 `orchestrator/application.yml`

## 前端

`ai-log-monitoring-ui/` — React 19 + Vite + TypeScript + Tailwind 4 + shadcn
包管理：pnpm
可视化库：React Flow, Monaco Editor, Cytoscape.js, Mermaid, ECharts
状态：骨架完成，页面待开发（仪表盘、事件中心、根因分析、配置）

## 未完成目标

### observe-engine
- 缺少 LOG_WARN / METRIC_ALERT / TRACE_ANOMALY / DEPLOY_EVENT / K8S_EVENT / CI_FAILURE / CONFIG_CHANGE 的 EventCollector 实现
- LogCollector 路径依赖 `observe.collector.log.path` 配置，需要 application.yml 配合
- Collector 缺少健康检查/熔断机制，单个 Collector 失败无告警
- ObserveScheduler 未配置可调度的 cron 表达式（目前固定 30s）

### context-builder
- ContextBuilderService 尚未订阅 EventBus，当前为显式调用 build()，需接入自动触发
- ContextPackage 输出后无消费方，需对接 root-cause-engine
- CodeContextProvider 基于简单类名正则匹配，未实现真实堆栈解析（解析全限定类名 → 文件路径）
- 缺少 Trace 链路数据（明确非本期范围，后续配合 OpenTelemetry）

### root-cause-engine
- 规则目前是硬编码关键词匹配，可扩展性有限
- 未接入实际 EventBus 事件流（目前为显式调用的 analyze()）
- 缺少多事件关联分析（目前单 Event → 单 Report）
- 证据链提取依赖 ContextPackage 的 data Map 中的 String 值，类型不安全
- 缺少 LLM 辅助分析（非本期范围）
