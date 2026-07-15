package com.ailog.common.event;

public enum EventType {
    LOG_ERROR,
    LOG_WARN,
    METRIC_ALERT,
    TRACE_ANOMALY,
    DEPLOY_EVENT,
    DB_SLOW_QUERY,
    REDIS_FAILURE,
    MQ_LAG,
    DOCKER_CRASH,
    K8S_EVENT,
    CI_FAILURE,
    CONFIG_CHANGE
}
