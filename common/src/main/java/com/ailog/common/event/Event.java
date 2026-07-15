package com.ailog.common.event;

import java.time.Instant;
import java.util.Map;

public class Event {

    private String id;
    private EventType type;
    private String serviceName;
    private String exceptionType;
    private String errorMessage;
    private Instant time;
    private String source;
    private Severity severity;
    private Map<String, Object> payload;

    public Event() {
    }

    public Event(String id, EventType type, String serviceName, String exceptionType, String errorMessage,
                 Instant time, String source, Severity severity, Map<String, Object> payload) {
        this.id = id;
        this.type = type;
        this.serviceName = serviceName;
        this.exceptionType = exceptionType;
        this.errorMessage = errorMessage;
        this.time = time;
        this.source = source;
        this.severity = severity;
        this.payload = payload;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public EventType getType() {
        return type;
    }

    public void setType(EventType type) {
        this.type = type;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getExceptionType() {
        return exceptionType;
    }

    public void setExceptionType(String exceptionType) {
        this.exceptionType = exceptionType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getTime() {
        return time;
    }

    public void setTime(Instant time) {
        this.time = time;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }
}
