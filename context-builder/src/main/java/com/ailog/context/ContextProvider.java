package com.ailog.context;

import com.ailog.common.event.Event;

public interface ContextProvider {

    String name();

    ContextData collect(Event event);
}
