package com.ailog.rootcause;

import com.ailog.context.ContextPackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RootCauseEngineService {

    private static final Logger log = LoggerFactory.getLogger(RootCauseEngineService.class);

    private final List<RootCauseAnalyzer> analyzers;

    public RootCauseEngineService(List<RootCauseAnalyzer> analyzers) {
        this.analyzers = analyzers;
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
