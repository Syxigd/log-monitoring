package com.ailog.common.event;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SimpleEventBus implements EventBus {

    private final Map<EventType, List<EventHandler>> handlers = new ConcurrentHashMap<>();
    private final List<EventHandler> allHandlers = new CopyOnWriteArrayList<>();

    @Override
    public void publish(Event event) {
        List<EventHandler> list = handlers.get(event.getType());
        if (list != null) {
            for (EventHandler handler : list) {
                try {
                    handler.handle(event);
                } catch (Exception e) {
                }
            }
        }
        for (EventHandler handler : allHandlers) {
            try {
                handler.handle(event);
            } catch (Exception e) {
            }
        }
    }

    @Override
    public void subscribe(EventType type, EventHandler handler) {
        handlers.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    @Override
    public void subscribeAll(EventHandler handler) {
        allHandlers.add(handler);
    }
}
