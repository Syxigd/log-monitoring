package com.ailog.context;

import com.ailog.common.event.Event;
import com.ailog.mcp.DockerTool;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class DockerContextProvider implements ContextProvider {

    private final DockerTool dockerTool;

    public DockerContextProvider(DockerTool dockerTool) {
        this.dockerTool = dockerTool;
    }

    @Override
    public String name() {
        return "docker";
    }

    @Override
    public ContextData collect(Event event) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("ps", dockerTool.ps());

            String containerId = event.getPayload() != null
                    ? (String) event.getPayload().get("containerId") : null;
            if (containerId != null) {
                data.put("logs", dockerTool.logs(containerId, 50));
            }

            return ContextData.success(name(), data);
        } catch (Exception e) {
            return ContextData.failure(name(), e.getMessage());
        }
    }
}
