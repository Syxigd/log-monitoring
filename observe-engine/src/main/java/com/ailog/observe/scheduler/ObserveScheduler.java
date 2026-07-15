package com.ailog.observe.scheduler;

import com.ailog.observe.ObserveEngineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class ObserveScheduler {

    private static final Logger log = LoggerFactory.getLogger(ObserveScheduler.class);

    private final ObserveEngineService observeEngineService;

    public ObserveScheduler(ObserveEngineService observeEngineService) {
        this.observeEngineService = observeEngineService;
    }

    @Scheduled(fixedDelayString = "${observe.collector.interval-ms:30000}")
    public void runCollectors() {
        log.debug("Running all collectors");
        observeEngineService.collectAll();
    }
}
