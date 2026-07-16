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
public class DbPerformanceRule implements RootCauseAnalyzer {

    @Override
    public RootCause rootCause() {
        return RootCause.DB_PERFORMANCE;
    }

    @Override
    public boolean evaluate(ContextPackage context) {
        if (context.getOriginalEvent().getType() != EventType.DB_SLOW_QUERY) return false;

        ContextData dbData = context.getContexts().get(ContextData.PROVIDER_DB);
        return dbData != null && dbData.isSuccess()
                && dbData.get(ContextData.DB_SLOW_SQL, String.class) != null;
    }

    @Override
    public RootCauseReport execute(ContextPackage context) {
        List<String> evidence = new ArrayList<>();
        com.ailog.common.event.Event event = context.getOriginalEvent();
        evidence.add("检测到数据库慢查询");

        ContextData dbData = context.getContexts().get(ContextData.PROVIDER_DB);
        if (dbData != null && dbData.isSuccess()) {
            String slowSql = dbData.get(ContextData.DB_SLOW_SQL, String.class);
            if (slowSql != null) evidence.add("慢查询详情：" + slowSql);

            String locks = dbData.get(ContextData.DB_LOCKS, String.class);
            if (locks != null && !locks.isBlank()) evidence.add("当前锁信息：" + locks);
        }

        return new RootCauseReport(
                event.getId(),
                RootCause.DB_PERFORMANCE,
                evidence,
                "mysql",
                "检查慢查询 SQL 并优化索引或查询语句"
        );
    }

    @Override public String getName() { return "dbPerformanceRule"; }
    @Override public String getDescription() { return "检测数据库性能瓶颈"; }
    @Override public int getPriority() { return 4; }
    @Override public boolean evaluate(Facts facts) { return evaluate((ContextPackage) facts.get("context")); }
    @Override public void execute(Facts facts) { facts.put("report", execute((ContextPackage) facts.get("context"))); }
}
