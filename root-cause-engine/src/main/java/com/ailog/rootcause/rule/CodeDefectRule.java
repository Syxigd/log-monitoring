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
public class CodeDefectRule implements RootCauseAnalyzer {

    @Override
    public RootCause rootCause() {
        return RootCause.CODE_DEFECT;
    }

    @Override
    public boolean evaluate(ContextPackage context) {
        String exceptionType = context.getOriginalEvent().getExceptionType();
        ContextData gitData = context.getContexts().get("git");
        return exceptionType != null && !exceptionType.isBlank()
                && gitData != null && gitData.isSuccess()
                && gitData.getData().containsKey("recentCommits");
    }

    @Override
    public RootCauseReport execute(ContextPackage context) {
        List<String> evidence = new ArrayList<>();
        Event event = context.getOriginalEvent();
        evidence.add(event.getServiceName() + " 出现 " + event.getExceptionType()
                + "：" + (event.getErrorMessage() != null ? event.getErrorMessage() : ""));

        ContextData gitData = context.getContexts().get("git");
        if (gitData != null && gitData.isSuccess()) {
            String commits = (String) gitData.getData().get("recentCommits");
            if (commits != null && !commits.isBlank()) {
                evidence.add("Git 记录显示近期提交：" + commits);
            }
        }

        return new RootCauseReport(
                event.getId(),
                RootCause.CODE_DEFECT,
                evidence,
                event.getServiceName(),
                "审查最近代码变更并回滚或修复"
        );
    }

    @Override public String getName() { return "codeDefectRule"; }
    @Override public String getDescription() { return "检测代码缺陷导致的异常"; }
    @Override public int getPriority() { return 1; }
    @Override public boolean evaluate(Facts facts) { return evaluate((ContextPackage) facts.get("context")); }
    @Override public void execute(Facts facts) { facts.put("report", execute((ContextPackage) facts.get("context"))); }
}
