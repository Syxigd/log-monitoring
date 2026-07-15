package com.ailog.mcp;

import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.protocol.body.Connection;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
public class MqTool {

    private final String nameServer;

    public MqTool(@Value("${rocketmq.name-server:}") String nameServer) {
        this.nameServer = nameServer;
    }

    @Tool(description = "List all RocketMQ consumer groups")
    public String consumers() {
        var admin = new DefaultMQAdminExt();
        try {
            admin.setNamesrvAddr(nameServer);
            admin.start();
            var clusterInfo = admin.examineBrokerClusterInfo();
            var brokerData = clusterInfo.getBrokerAddrTable().values().iterator().next();
            var brokerAddr = brokerData.selectBrokerAddr();
            var groups = admin.getAllSubscriptionGroup(brokerAddr, 3000);
            var table = groups.getSubscriptionGroupTable();
            if (table == null || table.isEmpty()) return "No consumer groups found";
            return table.keySet().stream().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        } finally {
            admin.shutdown();
        }
    }

    @Tool(description = "Check consumer connections for a consumer group")
    public String lag(String consumerGroup) {
        var admin = new DefaultMQAdminExt();
        try {
            admin.setNamesrvAddr(nameServer);
            admin.start();
            var info = admin.examineConsumerConnectionInfo(consumerGroup);
            var connections = info.getConnectionSet();
            if (connections == null || connections.isEmpty()) return "No consumers found";
            return connections.stream()
                    .map(c -> String.format("%s  %s  v%d",
                            c.getClientId(), c.getLanguage(), c.getVersion()))
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        } finally {
            admin.shutdown();
        }
    }

    @Tool(description = "Check retry messages for a consumer group")
    public String retry(String consumerGroup) {
        var admin = new DefaultMQAdminExt();
        try {
            admin.setNamesrvAddr(nameServer);
            admin.start();
            var retryTopic = "%RETRY%" + consumerGroup;
            var queue = new MessageQueue(retryTopic, "broker", 0);
            var total = admin.maxOffset(queue) - admin.minOffset(queue);
            return "Retry queue size: " + total;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        } finally {
            admin.shutdown();
        }
    }

    @Tool(description = "Check dead letter messages for a consumer group")
    public String deadLetter(String consumerGroup) {
        var admin = new DefaultMQAdminExt();
        try {
            admin.setNamesrvAddr(nameServer);
            admin.start();
            var dlqTopic = "%DLQ%" + consumerGroup;
            var queue = new MessageQueue(dlqTopic, "broker", 0);
            var total = admin.maxOffset(queue) - admin.minOffset(queue);
            return "Dead letter queue size: " + total;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        } finally {
            admin.shutdown();
        }
    }
}
