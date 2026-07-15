package com.ailog.context;

import java.util.Map;

public class ContextData {

    private final String providerName;
    private final Map<String, Object> data;
    private final boolean success;
    private final String errorMessage;

    public ContextData(String providerName, Map<String, Object> data, boolean success, String errorMessage) {
        this.providerName = providerName;
        this.data = data;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public static ContextData success(String providerName, Map<String, Object> data) {
        return new ContextData(providerName, data, true, null);
    }

    public static ContextData failure(String providerName, String errorMessage) {
        return new ContextData(providerName, Map.of(), false, errorMessage);
    }

    public String getProviderName() { return providerName; }
    public Map<String, Object> getData() { return data; }
    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }
}
