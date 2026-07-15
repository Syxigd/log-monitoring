package com.ailog.common.event;

public interface EventBus {

    void publish(Event event);

    void subscribe(EventType type, EventHandler handler);

    @FunctionalInterface
    interface EventHandler {
        void handle(Event event);
    }
}
