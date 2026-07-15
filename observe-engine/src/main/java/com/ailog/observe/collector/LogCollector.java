package com.ailog.observe.collector;

import com.ailog.common.event.Event;
import com.ailog.common.event.EventType;
import com.ailog.common.event.Severity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Component
public class LogCollector implements EventCollector {

    private static final Logger log = LoggerFactory.getLogger(LogCollector.class);
    private static final Pattern ERROR_PATTERN = Pattern.compile("\\bERROR\\b", Pattern.CASE_INSENSITIVE);

    private final String logPath;
    private final Map<String, Long> filePositions = new ConcurrentHashMap<>();

    public LogCollector(@Value("${observe.collector.log.path:}") String logPath) {
        this.logPath = logPath;
    }

    @Override
    public String name() {
        return "log";
    }

    @Override
    public List<Event> collect() {
        if (logPath == null || logPath.isBlank()) {
            return List.of();
        }

        List<Event> events = new ArrayList<>();
        Path dir = Paths.get(logPath);

        if (!Files.exists(dir)) {
            return List.of();
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.log")) {
            for (Path file : stream) {
                events.addAll(scanFile(file));
            }
        } catch (IOException e) {
            log.error("Failed to scan log directory: {}", e.getMessage());
        }

        return events;
    }

    private List<Event> scanFile(Path file) {
        List<Event> events = new ArrayList<>();
        String key = file.toAbsolutePath().toString();
        long lastPos = filePositions.getOrDefault(key, 0L);

        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            long len = raf.length();
            if (len < lastPos) {
                lastPos = 0;
            }
            raf.seek(lastPos);

            String line;
            while ((line = raf.readLine()) != null) {
                if (ERROR_PATTERN.matcher(line).find()) {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("file", key);
                    payload.put("line", line);
                    String service = key.contains("/") ? key.substring(key.lastIndexOf('/') + 1).replace(".log", "") : "unknown";
                    events.add(new Event(
                            UUID.randomUUID().toString(),
                            EventType.LOG_ERROR,
                            service,
                            "LogError",
                            line.length() > 200 ? line.substring(0, 200) : line,
                            Instant.now(),
                            key,
                            Severity.HIGH,
                            payload
                    ));
                }
            }
            filePositions.put(key, raf.getFilePointer());
        } catch (IOException e) {
            log.error("Failed to read log file {}: {}", key, e.getMessage());
        }

        return events;
    }
}
