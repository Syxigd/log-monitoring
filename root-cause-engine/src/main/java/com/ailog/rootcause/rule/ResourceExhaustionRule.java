package com.ailog.rootcause.rule;

import com.ailog.common.event.Event;
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
        ContextData dockerData = context.getContexts().get(ContextData.PROVIDER_DOCKER);
        if (dockerData != null && dockerData.isSuccess()) {
            String logs = dockerData.get(ContextData.DOCKER_LOGS, String.class);
            if (logs != null && (logs.contains("OOM") || logs.contains("oom") || logs.contains("kill"))) {
                return true;
            }
        }

        ContextData redisData = context.getContexts().get(ContextData.PROVIDER_REDIS);
        if (redisData != null && redisData.isSuccess()) {
            String memory = redisData.get(ContextData.REDIS_MEMORY, String.class);
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

        ContextData dockerData = context.getContexts().get(ContextData.PROVIDER_DOCKER);
        if (dockerData != null && dockerData.isSuccess()) {
            String logs = dockerData.get(ContextData.DOCKER_LOGS, String.class);
            if (logs != null && logs.contains("OOM")) {
                evidence.add("Docker 容器因 OOM 被杀死");
            }
        }

        ContextData redisData = context.getContexts().get(ContextData.PROVIDER_REDIS);
        if (redisData != null && redisData.isSuccess()) {
            String memory = redisData.get(ContextData.REDIS_MEMORY, String.class);
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
