/**
 * 目录结构: ElasticsearchIndexService/src/main/java/org/ls/indexer/config/properties/KafkaTopicProperties.java
 * 文件名称: KafkaTopicProperties.java
 * 开发时间: 2025-05-19 00:15:00 UTC/GMT+08:00
 * 作者: Gemini
 * 代码用途: Kafka Topic 相关配置属性类。
 */
package org.ls.indexer.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "dms.indexer.kafka")
public class KafkaTopicProperties {

    /**
     * 文件新增/更新事件的 Topic 名称。
     * 对应配置文件中的 dms.indexer.kafka.topic.upsert
     */
    private String upsertTopicName = "dms-file-upsert-events";

    /**
     * 文件删除事件的 Topic 名称。
     * 对应配置文件中的 dms.indexer.kafka.topic.delete
     */
    private String deleteTopicName = "dms-file-delete-events";

    /**
     * 文件新增/更新事件的死信队列 (DLQ) Topic 名称。
     * 对应配置文件中的 dms.indexer.kafka.topic.upsert-dlq
     */
    private String upsertDlqTopicName = "dms-file-upsert-events-dlq";

    /**
     * 文件删除事件的死信队列 (DLQ) Topic 名称。
     * 对应配置文件中的 dms.indexer.kafka.topic.delete-dlq
     */
    private String deleteDlqTopicName = "dms-file-delete-events-dlq";

    /**
     * Kafka Topic 的默认分区数。
     * 对应配置文件中的 dms.indexer.kafka.partitions
     */
    private int defaultPartitions = 3;

    /**
     * Kafka Topic 的默认副本因子。
     * 对应配置文件中的 dms.indexer.kafka.replicas
     * 对于单节点Kafka集群，此值应为1。
     */
    private int defaultReplicas = 1;

    /**
     * DLQ Topic 的分区数，通常设置为1。
     */
    private int dlqPartitions = 1;

}
