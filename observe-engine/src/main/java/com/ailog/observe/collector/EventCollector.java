package com.ailog.observe.collector;

import com.ailog.common.event.Event;

import java.util.List;

public interface EventCollector {

    String name();

    List<Event> collect();
}
