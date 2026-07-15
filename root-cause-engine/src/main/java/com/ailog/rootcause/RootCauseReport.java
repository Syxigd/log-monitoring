package com.ailog.rootcause;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class RootCauseReport {

    private final String eventId;
    private final RootCause rootCause;
    private final List<String> evidence;
    private final String affectedService;
    private final String suggestedAction;
    private final Instant analyzedAt;

    public RootCauseReport(String eventId, RootCause rootCause, List<String> evidence,
                           String affectedService, String suggestedAction) {
        this.eventId = eventId;
        this.rootCause = rootCause;
        this.evidence = new ArrayList<>(evidence);
        this.affectedService = affectedService;
        this.suggestedAction = suggestedAction;
        this.analyzedAt = Instant.now();
    }

    public static RootCauseReport unknown(String eventId) {
        return new RootCauseReport(eventId, RootCause.UNKNOWN, List.of(), "unknown", "无法识别根因，请人工排查");
    }

    public String getEventId() { return eventId; }
    public RootCause getRootCause() { return rootCause; }
    public List<String> getEvidence() { return evidence; }
    public String getAffectedService() { return affectedService; }
    public String getSuggestedAction() { return suggestedAction; }
    public Instant getAnalyzedAt() { return analyzedAt; }
}
