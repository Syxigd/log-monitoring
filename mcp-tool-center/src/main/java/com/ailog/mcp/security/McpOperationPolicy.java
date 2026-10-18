package com.ailog.mcp.security;

public interface McpOperationPolicy {

    boolean isAllowed(String operation, String actor, String requestId);

    void record(McpAuditRecord record);
}
