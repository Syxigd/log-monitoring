package com.ailog.context;

import com.ailog.common.event.Event;
import com.ailog.mcp.RedisTool;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class RedisContextProvider implements ContextProvider {

    private final RedisTool redisTool;

    public RedisContextProvider(RedisTool redisTool) {
        this.redisTool = redisTool;
    }

    @Override
    public String name() {
        return "redis";
    }

    @Override
    public ContextData collect(Event event) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("memory", redisTool.memory());
            data.put("clusterInfo", redisTool.clusterInfo());
            return ContextData.success(name(), data);
        } catch (Exception e) {
            return ContextData.failure(name(), e.getMessage());
        }
    }
}
