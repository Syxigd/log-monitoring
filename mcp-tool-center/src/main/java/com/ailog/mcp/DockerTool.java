package com.ailog.mcp;

import com.ailog.mcp.security.McpOperationPolicy;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DockerTool {

    private final DockerClient dockerClient;
    private final McpOperationPolicy operationPolicy;

    public DockerTool(McpOperationPolicy operationPolicy) {
        this.operationPolicy = operationPolicy;
        var config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        var httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .build();
        this.dockerClient = DockerClientImpl.getInstance(config, httpClient);
    }

    @Tool(description = "List running Docker containers")
    public String ps() {
        List<Container> containers = dockerClient.listContainersCmd().exec();
        if (containers.isEmpty()) {
            return "No running containers";
        }
        return containers.stream()
                .map(c -> String.format("%s  %s  %s", c.getId().substring(0, 12),
                        String.join(", ", c.getNames()),
                        c.getState()))
                .collect(Collectors.joining("\n"));
    }

    @Tool(description = "View logs of a Docker container")
    public String logs(String containerId, @org.springframework.ai.tool.annotation.ToolParam(description = "Number of tail lines") int tail) {
        try {
            var sb = new StringBuilder();
            dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withTail(tail)
                    .exec(new LogContainerResultCallback() {
                        @Override
                        public void onNext(Frame frame) {
                            sb.append(new String(frame.getPayload()));
                            super.onNext(frame);
                        }
                    })
                    .awaitCompletion();
            return sb.toString();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Restart a Docker container")
    public String restart(String containerId) {
        String requestId = UUID.randomUUID().toString();
        if (!operationPolicy.isAllowed("docker.restart", "mcp-tool", requestId)) {
            return "Denied: docker.restart is disabled by MCP security policy (requestId=" + requestId + ")";
        }
        dockerClient.restartContainerCmd(containerId).exec();
        return "Container " + containerId.substring(0, 12) + " restarted";
    }

    @Tool(description = "Inspect a Docker container details")
    public String inspect(String containerId) {
        var info = dockerClient.inspectContainerCmd(containerId).exec();
        return String.format("Name: %s\nStatus: %s\nImage: %s\nIP: %s",
                info.getName(),
                info.getState().getStatus(),
                info.getConfig().getImage(),
                info.getNetworkSettings().getIpAddress());
    }
}
