package com.ailog.context;

import java.util.Map;

public class ContextData {

    public static final String GIT_COMMITS = "recentCommits";
    public static final String GIT_CHANGES = "recentChanges";
    public static final String DOCKER_PS = "ps";
    public static final String DOCKER_LOGS = "logs";
    public static final String DB_SLOW_SQL = "slowSql";
    public static final String DB_LOCKS = "locks";
    public static final String REDIS_MEMORY = "memory";
    public static final String REDIS_CLUSTER = "clusterInfo";
    public static final String MQ_LAG = "lag";
    public static final String MQ_RETRY = "retry";
    public static final String ENV_JAVA_VERSION = "javaVersion";

    public static final String PROVIDER_GIT = "git";
    public static final String PROVIDER_DOCKER = "docker";
    public static final String PROVIDER_DB = "db";
    public static final String PROVIDER_REDIS = "redis";
    public static final String PROVIDER_MQ = "mq";
    public static final String PROVIDER_CODE = "code";
    public static final String PROVIDER_ENV = "env";

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

    public <T> T get(String key, Class<T> type) {
        Object value = data.get(key);
        if (value == null) return null;
        return type.cast(value);
    }

    public String getProviderName() { return providerName; }
    public Map<String, Object> getData() { return data; }
    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }
}
