package com.ailog.mcp;

import com.ailog.mcp.security.McpOperationPolicy;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.net.URI;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RustfsTool {

    private final S3Client s3Client;
    private final String bucket;
    private final McpOperationPolicy operationPolicy;

    public RustfsTool(
            @Value("${rustfs.endpoint}") String endpoint,
            @Value("${rustfs.access-key}") String accessKey,
            @Value("${rustfs.secret-key}") String secretKey,
            @Value("${rustfs.bucket}") String bucket,
            McpOperationPolicy operationPolicy) {
        this.bucket = bucket;
        this.operationPolicy = operationPolicy;
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(true)
                .build();
    }

    @Tool(description = "List files in RustFS bucket")
    public String list(String prefix) {
        var request = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(prefix)
                .maxKeys(50)
                .build();
        var response = s3Client.listObjectsV2(request);
        if (!response.hasContents()) return "No files found";
        return response.contents().stream()
                .map(o -> String.format("%s  (%,d bytes)", o.key(), o.size()))
                .collect(Collectors.joining("\n"));
    }

    @Tool(description = "Get file content from RustFS")
    public String get(String key) {
        try {
            var response = s3Client.getObject(b -> b.bucket(bucket).key(key));
            return new String(response.readAllBytes());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Upload content to RustFS")
    public String put(String key, String content) {
        String requestId = UUID.randomUUID().toString();
        if (!operationPolicy.isAllowed("rustfs.put", "mcp-tool", requestId)) {
            return "Denied: rustfs.put is disabled by MCP security policy (requestId=" + requestId + ")";
        }
        try {
            s3Client.putObject(b -> b.bucket(bucket).key(key),
                    software.amazon.awssdk.core.sync.RequestBody.fromString(content));
            return "Uploaded: " + key;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Delete a file from RustFS")
    public String delete(String key) {
        String requestId = UUID.randomUUID().toString();
        if (!operationPolicy.isAllowed("rustfs.delete", "mcp-tool", requestId)) {
            return "Denied: rustfs.delete is disabled by MCP security policy (requestId=" + requestId + ")";
        }
        try {
            s3Client.deleteObject(b -> b.bucket(bucket).key(key));
            return "Deleted: " + key;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
