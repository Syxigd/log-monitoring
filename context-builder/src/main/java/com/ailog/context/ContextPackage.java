package com.ailog.context;

import com.ailog.common.event.Event;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class ContextPackage {

    private final String eventId;
    private final Event originalEvent;
    private final Map<String, ContextData> contexts = new HashMap<>();
    private final Instant collectedAt;

    public ContextPackage(Event originalEvent) {
        this.eventId = originalEvent.getId();
        this.originalEvent = originalEvent;
        this.collectedAt = Instant.now();
    }

    public void addContext(ContextData data) {
        contexts.put(data.getProviderName(), data);
    }

    public String getEventId() { return eventId; }
    public Event getOriginalEvent() { return originalEvent; }
    public Map<String, ContextData> getContexts() { return contexts; }
    public Instant getCollectedAt() { return collectedAt; }
}
