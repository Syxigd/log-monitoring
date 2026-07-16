package com.ailog.rootcause;

import com.ailog.common.event.Event;
import com.ailog.common.event.EventBus;
import com.ailog.context.ContextBuilderService;
import com.ailog.context.ContextPackage;
import com.ailog.rootcause.config.RuleConfigProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RootCauseEngineService {

    private static final Logger log = LoggerFactory.getLogger(RootCauseEngineService.class);

    private final List<RootCauseAnalyzer> analyzers;
    private final ContextBuilderService contextBuilder;
    private final EventBus eventBus;

    public RootCauseEngineService(List<RootCauseAnalyzer> analyzers,
                                   ContextBuilderService contextBuilder,
                                   EventBus eventBus,
                                   RuleConfigProperties ruleConfig) {
        this.contextBuilder = contextBuilder;
        this.eventBus = eventBus;
        this.analyzers = analyzers.stream()
                .filter(a -> isEnabled(a, ruleConfig))
                .sorted(Comparator.comparingInt(a -> getPriority(a, ruleConfig)))
                .collect(Collectors.toList());
    }

    private boolean isEnabled(RootCauseAnalyzer analyzer, RuleConfigProperties config) {
        RuleConfigProperties.RuleConfig rc = config.getRules().get(analyzer.getName());
        return rc == null || rc.isEnabled();
    }

    private int getPriority(RootCauseAnalyzer analyzer, RuleConfigProperties config) {
        RuleConfigProperties.RuleConfig rc = config.getRules().get(analyzer.getName());
        if (rc != null) {
            return rc.getPriority();
        }
        return analyzer.getPriority();
    }

    @PostConstruct
    public void subscribeToEvents() {
        eventBus.subscribeAll(this::onEvent);
        log.info("RootCauseEngineService subscribed to all EventBus events ({} analyzers active)", analyzers.size());
    }

    private void onEvent(Event event) {
        try {
            ContextPackage context = contextBuilder.build(event);
            RootCauseReport report = analyze(context);
            log.info("Auto-analysis complete: {} for event {} (service: {})",
                    report.getRootCause(), event.getId(), event.getServiceName());
        } catch (Exception e) {
            log.error("Auto-analysis failed for event {}: {}", event.getId(), e.getMessage());
        }
    }

    public RootCauseReport analyze(ContextPackage context) {
        String eventId = context.getOriginalEvent().getId();
        RootCauseReport layer2Match = null;

        for (RootCauseAnalyzer analyzer : analyzers) {
            try {
                if (!analyzer.evaluate(context)) continue;

                RootCauseReport report = analyzer.execute(context);
                RootCause cause = report.getRootCause();

                if (cause == RootCause.CODE_DEFECT
                        || cause == RootCause.RESOURCE_EXHAUSTION
                        || cause == RootCause.CONFIG_ERROR) {
                    log.info("Root cause identified: {} for event {}", cause, eventId);
                    return report;
                }

                if (layer2Match == null || report.getEvidence().size() > layer2Match.getEvidence().size()) {
                    layer2Match = report;
                }
            } catch (Exception e) {
                log.warn("Analyzer {} failed for event {}: {}", analyzer.getName(), eventId, e.getMessage());
            }
        }

        if (layer2Match != null) {
            log.info("Root cause identified (layer 2): {} for event {}", layer2Match.getRootCause(), eventId);
            return layer2Match;
        }

        log.warn("No root cause identified for event {}", eventId);
        return RootCauseReport.unknown(eventId);
    }
}
