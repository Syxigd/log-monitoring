package com.ailog.mcp.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;

@Service
public class DefaultMcpOperationPolicy implements McpOperationPolicy {

    private static final Logger log = LoggerFactory.getLogger(DefaultMcpOperationPolicy.class);
    private static final Set<String> READ_ONLY_OPERATIONS = Set.of(
            "docker.ps", "docker.logs", "docker.inspect",
            "mysql.query", "mysql.tables", "mysql.indexes", "mysql.locks", "mysql.slowSql",
            "redis.keys", "redis.ttl", "redis.memory", "redis.clusterInfo", "redis.monitor",
            "mq.consumers", "mq.lag", "mq.retry", "mq.deadLetter",
            "rustfs.list", "rustfs.get");

    private final McpSecurityProperties properties;

    public DefaultMcpOperationPolicy(McpSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean isAllowed(String operation, String actor, String requestId) {
        McpRiskLevel riskLevel = riskLevelOf(operation);
        boolean allowed = switch (operation) {
            case "docker.restart" -> properties.getDocker().isRestartEnabled();
            case "rustfs.delete" -> properties.getRustfs().isDeleteEnabled();
            default -> READ_ONLY_OPERATIONS.contains(operation);
        };
        String reason = allowed ? "allowed" : "operation disabled by policy";
        record(new McpAuditRecord(Instant.now(), operation, riskLevel, actor, requestId,
                targetTypeOf(operation), allowed, reason));
        return allowed;
    }

    @Override
    public void record(McpAuditRecord record) {
        log.info("MCP audit operation={} risk={} actor={} requestId={} target={} allowed={} reason={}",
                record.operation(), record.riskLevel(), record.actor(), record.requestId(), record.target(),
                record.allowed(), record.reason());
    }

    static McpRiskLevel riskLevelOf(String operation) {
        return switch (operation) {
            case "docker.restart", "rustfs.delete" -> McpRiskLevel.DESTRUCTIVE;
            case "rustfs.put" -> McpRiskLevel.MUTATING;
            default -> READ_ONLY_OPERATIONS.contains(operation)
                    ? McpRiskLevel.READ_ONLY : McpRiskLevel.MUTATING;
        };
    }

    private static String targetTypeOf(String operation) {
        if (operation.startsWith("docker.")) return "docker-container";
        if (operation.startsWith("rustfs.")) return "rustfs-object";
        return "service-resource";
    }
}
