package com.ailog.rootcause.rule;

import com.ailog.common.event.Event;
import com.ailog.context.ContextPackage;
import com.ailog.rootcause.RootCause;
import com.ailog.rootcause.RootCauseAnalyzer;
import com.ailog.rootcause.RootCauseReport;
import org.jeasy.rules.api.Facts;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ConfigErrorRule implements RootCauseAnalyzer {

    private static final List<String> CONFIG_KEYWORDS = List.of("config", "FileNotFound", "NoSuchFile",
            "application.yml", "application.properties", "not found", "InvalidConfig");

    @Override
    public RootCause rootCause() {
        return RootCause.CONFIG_ERROR;
    }

    @Override
    public boolean evaluate(ContextPackage context) {
        String errorMessage = context.getOriginalEvent().getErrorMessage();
        if (errorMessage == null || errorMessage.isBlank()) return false;

        return CONFIG_KEYWORDS.stream().anyMatch(kw ->
                errorMessage.toLowerCase().contains(kw.toLowerCase()));
    }

    @Override
    public RootCauseReport execute(ContextPackage context) {
        List<String> evidence = new ArrayList<>();
        Event event = context.getOriginalEvent();
        evidence.add("错误信息：" + event.getErrorMessage());
        evidence.add("可能存在配置缺失或路径错误，建议检查配置文件与环境变量");

        return new RootCauseReport(
                event.getId(),
                RootCause.CONFIG_ERROR,
                evidence,
                event.getServiceName(),
                "检查配置文件路径与内容是否正确"
        );
    }

    @Override public String getName() { return "configErrorRule"; }
    @Override public String getDescription() { return "检测配置错误导致的异常"; }
    @Override public int getPriority() { return 3; }
    @Override public boolean evaluate(Facts facts) { return evaluate((ContextPackage) facts.get("context")); }
    @Override public void execute(Facts facts) { facts.put("report", execute((ContextPackage) facts.get("context"))); }
}
