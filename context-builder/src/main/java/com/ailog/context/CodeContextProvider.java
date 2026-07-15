package com.ailog.context;

import com.ailog.common.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
public class CodeContextProvider implements ContextProvider {

    private static final Logger log = LoggerFactory.getLogger(CodeContextProvider.class);
    private static final Pattern CLASS_PATTERN = Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*)\\.java");

    private final String sourcePath;

    public CodeContextProvider(@Value("${context.code.source-path:src/main/java}") String sourcePath) {
        this.sourcePath = sourcePath;
    }

    @Override
    public String name() {
        return "code";
    }

    @Override
    public ContextData collect(Event event) {
        try {
            Map<String, Object> data = new HashMap<>();
            String errorMessage = event.getErrorMessage();
            if (errorMessage != null && !errorMessage.isBlank()) {
                String className = extractClassName(errorMessage);
                if (className != null) {
                    data.put("relatedSources", findSourceFiles(className));
                }
            }
            return ContextData.success(name(), data);
        } catch (Exception e) {
            return ContextData.failure(name(), e.getMessage());
        }
    }

    private String extractClassName(String text) {
        Matcher m = CLASS_PATTERN.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private String findSourceFiles(String className) {
        Path base = Paths.get(sourcePath);
        if (!Files.exists(base)) return "Source path not found: " + sourcePath;

        try (Stream<Path> walk = Files.walk(base)) {
            return walk.filter(p -> p.getFileName().toString().equals(className + ".java"))
                    .limit(3)
                    .map(p -> {
                        try {
                            return "--- " + p + " ---\n" + Files.readString(p);
                        } catch (IOException e) {
                            return "Error reading " + p + ": " + e.getMessage();
                        }
                    })
                    .collect(java.util.stream.Collectors.joining("\n\n"));
        } catch (IOException e) {
            return "Error scanning sources: " + e.getMessage();
        }
    }
}
