package com.ailog.context;

import com.ailog.common.event.Event;
import com.ailog.mcp.MqTool;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class MqContextProvider implements ContextProvider {

    private final MqTool mqTool;

    public MqContextProvider(MqTool mqTool) {
        this.mqTool = mqTool;
    }

    @Override
    public String name() {
        return "mq";
    }

    @Override
    public ContextData collect(Event event) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put(ContextData.MQ_LAG, mqTool.lag("DEFAULT_CONSUMER"));
            data.put(ContextData.MQ_RETRY, mqTool.retry("DEFAULT_CONSUMER"));
            return ContextData.success(name(), data);
        } catch (Exception e) {
            return ContextData.failure(name(), e.getMessage());
        }
    }
}
