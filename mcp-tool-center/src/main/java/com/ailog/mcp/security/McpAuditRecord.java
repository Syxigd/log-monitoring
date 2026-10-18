package com.ailog.mcp.security;

import java.time.Instant;

public record McpAuditRecord(
        Instant timestamp,
        String operation,
        McpRiskLevel riskLevel,
        String actor,
        String requestId,
        String target,
        boolean allowed,
        String reason) {
}
