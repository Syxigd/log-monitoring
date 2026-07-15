package com.ailog.context;

import com.ailog.common.event.Event;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class EnvContextProvider implements ContextProvider {

    @Override
    public String name() {
        return "env";
    }

    @Override
    public ContextData collect(Event event) {
        Map<String, Object> data = new HashMap<>();
        data.put("javaVersion", System.getProperty("java.version", "unknown"));
        data.put("osName", System.getProperty("os.name", "unknown"));
        data.put("osArch", System.getProperty("os.arch", "unknown"));
        data.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        data.put("maxMemory", Runtime.getRuntime().maxMemory());
        return ContextData.success(name(), data);
    }
}
