package com.ailog.observe;

import com.ailog.common.event.Event;
import com.ailog.common.event.EventBus;
import com.ailog.observe.collector.EventCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ObserveEngineService {

    private static final Logger log = LoggerFactory.getLogger(ObserveEngineService.class);

    private final List<EventCollector> collectors;
    private final EventBus eventBus;

    public ObserveEngineService(List<EventCollector> collectors, EventBus eventBus) {
        this.collectors = collectors;
        this.eventBus = eventBus;
    }

    public void collectAll() {
        for (EventCollector collector : collectors) {
            try {
                List<Event> events = collector.collect();
                for (Event event : events) {
                    eventBus.publish(event);
                }
            } catch (Exception e) {
                log.error("Collector {} failed: {}", collector.name(), e.getMessage());
            }
        }
    }
}
