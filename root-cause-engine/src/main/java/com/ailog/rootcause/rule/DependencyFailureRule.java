package com.ailog.rootcause.rule;

import com.ailog.common.event.EventType;
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
        com.ailog.common.event.Event event = context.getOriginalEvent();
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
