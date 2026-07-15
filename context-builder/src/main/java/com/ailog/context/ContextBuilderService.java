package com.ailog.context;

import com.ailog.common.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ContextBuilderService {

    private static final Logger log = LoggerFactory.getLogger(ContextBuilderService.class);

    private final List<ContextProvider> providers;

    public ContextBuilderService(List<ContextProvider> providers) {
        this.providers = providers;
    }

    public ContextPackage build(Event event) {
        ContextPackage pkg = new ContextPackage(event);
        for (ContextProvider provider : providers) {
            try {
                ContextData data = provider.collect(event);
                pkg.addContext(data);
                log.debug("Provider {} completed for event {}", provider.name(), event.getId());
            } catch (Exception e) {
                log.error("Provider {} failed for event {}: {}", provider.name(), event.getId(), e.getMessage());
                pkg.addContext(ContextData.failure(provider.name(), e.getMessage()));
            }
        }
        return pkg;
    }
}
