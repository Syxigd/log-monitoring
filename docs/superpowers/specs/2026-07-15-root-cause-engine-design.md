# Root Cause Engine — 第一期设计

## 目标

接收 ContextPackage（来自 context-builder），通过确定性证据链匹配推断根因，输出结构化 RootCauseReport 供 planner 使用。

## 输入与输出

- **输入:** `ContextPackage`（包含原始 Event + 7 个 Provider 的多源上下文数据）
- **输出:** `RootCauseReport`（根因类型、证据链、影响范围、建议动作）

## 技术选型

**Easy Rules** 4.x 作为规则引擎。每条规则是一个 POJO：
- `evaluate()` — 检查证据是否存在（返回 boolean）
- `execute()` — 构造证据链（证据是一系列字符串）
- 规则间无状态共享，通过优先级排序

## 架构

```
ContextPackage → RootCauseEngineService
                    ├── CodeDefectRule        (优先级 1)
                    ├── ResourceExhaustionRule (优先级 2)
                    ├── ConfigErrorRule        (优先级 3)
                    ├── DbPerformanceRule      (优先级 4)
                    └── DependencyFailureRule   (优先级 5)
                         ↓
                  RootCauseReport
```

### RootCauseEngineService

- 注入所有规则 Bean
- `analyze(ContextPackage) → RootCauseReport`
- 按优先级运行所有规则
- 先跑第一层（CODE_DEFECT / RESOURCE_EXHAUSTION / CONFIG_ERROR），有命中则直接返回
- 第一层全未命中 → 跑第二层（DB_PERFORMANCE / DEPENDENCY_FAILURE），取证据链最长的
- 全未命中 → UNKNOWN

## 核心模型

```java
public enum RootCause {
    CODE_DEFECT,        // 代码缺陷
    RESOURCE_EXHAUSTION, // 资源耗尽
    CONFIG_ERROR,        // 配置错误
    DB_PERFORMANCE,      // 数据库性能瓶颈
    DEPENDENCY_FAILURE,  // 依赖服务故障
    UNKNOWN              // 无法识别
}

public class RootCauseReport {
    private String eventId;
    private RootCause rootCause;
    private List<String> evidence;       // 证据链，如 ["Git commit abc123 修改了 OrderService.java"]
    private String affectedService;      // 影响范围
    private String suggestedAction;      // 建议动作（给 planner 用）
    private Instant analyzedAt;
}
```

## 规则设计

### 第一层：直接根因

| 规则 | 证据条件 | 证据链示例 |
|------|----------|-----------|
| **CodeDefectRule** | (1) Event.exceptionType 非空 (2) GitContext 有最近提交匹配该模块 | `["服务 order-service 在 15:30 出现 NullPointerException", "Git 记录显示 5 分钟前提交了 OrderService.java"]` |
| **ResourceExhaustionRule** | (1) Docker crash 日志含 OOM/kill (2) Redis 内存告警 | `["容器 order-service 因 OOM 被杀死", "Redis 内存使用率 95%"]` |
| **ConfigErrorRule** | (1) errorMessage 含 config/FileNotFound/Invalid (2) Env 显示配置路径 | `["错误信息提示配置文件未找到", "环境变量中配置路径为 /etc/app.yml"]` |

### 第二层：影响/症状

| 规则 | 证据条件 | 证据链示例 |
|------|----------|-----------|
| **DbPerformanceRule** | (1) EventType=DB_SLOW_QUERY (2) DbContext 含慢查询 + 锁 | `["检测到 3 条慢查询（最长 12s）", "当前锁等待数为 2"]` |
| **DependencyFailureRule** | (1) EventType=MQ_LAG (2) MqContext 显示重试积压 | `["RocketMQ 消费延迟 5000 条", "重试队列积压 200 条"]` |

### 判定逻辑

```
evidence = runAllRules(contextPackage)

if evidence.rootCause ∈ {CODE_DEFECT, RESOURCE_EXHAUSTION, CONFIG_ERROR}:
    → 直接作为最终根因

elif evidence.rootCause ≠ UNKNOWN (第二层规则命中):
    → 取证据链最长的作为根因

else:
    → UNKNOWN
```

## 非本期范围

- 多事件关联分析（目前单 Event → 单 Report，后续多个 Event 合并分析）
- 学习引擎反馈闭环（根因判定的正确性回馈给 learning-engine）
- 规则热加载/动态编排
- LLM 辅助分析

## 对 pom.xml 的改动

```xml
<dependency>
    <groupId>org.jeasy</groupId>
    <artifactId>easy-rules-core</artifactId>
    <version>4.1.0</version>
</dependency>
<dependency>
    <groupId>com.ailog</groupId>
    <artifactId>context-builder</artifactId>
    <version>${project.version}</version>
</dependency>
```

## 对 context-builder 的依赖

RootCauseEngineService 需要订阅 EventBus、调用 ContextBuilderService.build()。这意味着 context-builder 需要对外暴露 `ContextBuilderService`（当前已暴露），且 root-cause-engine 需要在 pom.xml 中引入 context-builder。
