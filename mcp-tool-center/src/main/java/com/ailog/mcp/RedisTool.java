package com.ailog.mcp;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class RedisTool {

    private final StringRedisTemplate redisTemplate;

    public RedisTool(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Tool(description = "List Redis keys matching a pattern")
    public String keys(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys == null || keys.isEmpty()) return "No keys found";
        return String.join("\n", keys);
    }

    @Tool(description = "Get TTL of a Redis key in seconds")
    public String ttl(String key) {
        Long ttl = redisTemplate.getExpire(key);
        return ttl != null ? ttl + "s" : "Key not found or no expiry";
    }

    @Tool(description = "Get Redis memory usage info")
    public String memory() {
        var info = redisTemplate.getConnectionFactory().getConnection().info("memory");
        if (info == null) return "No memory info";
        return info.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    @Tool(description = "Get Redis cluster info")
    public String clusterInfo() {
        var info = redisTemplate.getConnectionFactory().getConnection().info("server");
        if (info == null) return "No cluster info";
        return info.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    @Tool(description = "Monitor Redis slow queries (default threshold 200ms)")
    public String monitor() {
        var slow = redisTemplate.getConnectionFactory().getConnection()
                .serverCommands().getConfig("slowlog-max-len");
        return "slowlog-max-len: " + (slow.isEmpty() ? "not set" : slow.get(0));
    }
}
