# AI Log Monitoring — 第一期基础设施设计

## 项目定位

AIOps Software Engineering Agent Platform（智能软件工程平台），代号 SE-Agent。
实现从日志监控到自动诊断、修复、验证、学习的完整软件工程 AI 闭环。

## 技术栈

### 后端

- Java 21 + Spring Boot 3.x + Spring AI
- Maven 多模块

| 组件 | 用途 |
|------|------|
| PostgreSQL | 业务数据 |
| Neo4j | 知识图谱 |
| Redis | 缓存与任务状态 |
| RocketMQ | 事件总线 |
| rustfs | 对象存储 |

### AI 能力

- 多模型路由（OpenAI、Claude、Gemini、本地模型）
- 向量数据库 pgvector（PostgreSQL 扩展）
- MCP Tool Server
- Prompt Registry
- Memory Engine

### 前端

- React 19 + TypeScript + Vite（已初始化）
- shadcn-ui + Tailwind CSS（已初始化）
- React Flow（流程可视化，待安装）
- Monaco Editor（代码查看，待安装）
- Cytoscape.js（知识图谱，待安装）
- Mermaid（流程图，待安装）
- ECharts（监控大盘，待安装）

## 模块结构

第一期共 10 个 Maven 子模块：

| 模块 | 职责 |
|------|------|
| `common` | 共享模型（Event、DTO、枚举、工具类） |
| `observe-engine` | 监听日志/Trace/Metrics/数据库/Redis 等所有事件 |
| `context-builder` | 收到事件后拼接上下文（Trace、请求参数、Git、代码、环境等） |
| `code-knowledge-graph` | 代码知识图谱（类/方法/SQL/API/DTO 的关系图） |
| `root-cause-engine` | 根因分析（Tree Search 方式逐层追问） |
| `planner` | 根据根因生成多个修复方案并评估 |
| `executor` | 工作流执行器（Git Checkout → 改代码 → 测试 → 构建） |
| `mcp-tool-center` | 统一工具层（Docker/MySQL/Redis/MQ 等工具封装） |
| `dashboard` | 后端 API + WebSocket，为前端提供数据接口 |
| `orchestrator` | AI 状态机编排器，连接所有模块 |

## 第一期交付物

1. **Parent POM** — Java 21 + Spring Boot 3.x + 子模块管理
2. **common** — Event 模型、Severity 枚举、Result 统一返回、异常定义
3. **10 个模块骨架** — 每个模块包含：
   - `src/main/java/com/ailog/<module>/` 包结构
   - 基础 Application 启动类（Spring Boot 入口）
   - `application.yml` 配置文件
   - `src/test/java/com/ailog/<module>/` 测试目录
4. **orchestrator** — 状态机骨架（6 个状态：Observe → Context → Diagnose → Plan → Execute → Validate）
5. **dashboard** — 健康检查端点 + 基础 WebSocket 配置
6. **事件驱动骨架** — common 模块中的 Event 模型和 EventBus 接口

## 架构约定

- 整个系统事件驱动：所有模块通过 Event 通信
- 模块之间依赖 common，不直接相互依赖
- orchestrator 感知所有模块，其他模块不感知 orchestrator
- dashboard 通过 WebSocket 推送事件流到前端
