package com.ailog.context;

import com.ailog.common.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class GitContextProvider implements ContextProvider {

    private static final Logger log = LoggerFactory.getLogger(GitContextProvider.class);

    @Override
    public String name() {
        return "git";
    }

    @Override
    public ContextData collect(Event event) {
        try {
            Map<String, Object> data = new HashMap<>();
            String serviceName = event.getServiceName();
            if (serviceName != null && !serviceName.isBlank()) {
                data.put(ContextData.GIT_COMMITS, exec("git log --oneline -10 -- " + serviceName));
                data.put(ContextData.GIT_CHANGES, exec("git diff --name-only HEAD~5 HEAD"));
            }
            return ContextData.success(name(), data);
        } catch (Exception e) {
            log.warn("Git context collection failed: {}", e.getMessage());
            return ContextData.failure(name(), e.getMessage());
        }
    }

    private String exec(String cmd) {
        try {
            Process process = Runtime.getRuntime().exec(cmd);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
