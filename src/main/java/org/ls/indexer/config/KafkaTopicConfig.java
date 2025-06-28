/**
 * 目录结构: ElasticsearchIndexService/src/main/java/org/ls/indexer/config/KafkaTopicConfig.java
 * 文件名称: KafkaTopicConfig.java
 * 开发时间: 2025-05-19 00:15:05 UTC/GMT+08:00
 * 作者: Gemini
 * 代码用途: 配置 Kafka Topics 的自动创建。
 */
package org.ls.indexer.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.ls.indexer.config.properties.KafkaTopicProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    private static final Logger logger = LoggerFactory.getLogger(KafkaTopicConfig.class);
    private final KafkaTopicProperties kafkaTopicProperties;

    @Autowired
    public KafkaTopicConfig(KafkaTopicProperties kafkaTopicProperties) {
        this.kafkaTopicProperties = kafkaTopicProperties;
    }

    /**
     * 定义文件新增/更新事件的 Topic。
     * Topic 名称、分区数和副本数从 {@link KafkaTopicProperties} 中获取。
     *
     * @return NewTopic bean for dms-file-upsert-events
     */
    @Bean
    public NewTopic fileUpsertEventsTopic() {
        logger.info("定义 Kafka Topic: {}, 分区数: {}, 副本数: {}",
                kafkaTopicProperties.getUpsertTopicName(),
                kafkaTopicProperties.getDefaultPartitions(),
                kafkaTopicProperties.getDefaultReplicas());
        return TopicBuilder.name(kafkaTopicProperties.getUpsertTopicName())
                .partitions(kafkaTopicProperties.getDefaultPartitions())
                .replicas(kafkaTopicProperties.getDefaultReplicas())
                .build();
    }

    /**
     * 定义文件新增/更新事件的死信队列 (DLQ) Topic。
     * DLQ Topic 通常使用较少的分区数（例如1）。
     *
     * @return NewTopic bean for dms-file-upsert-events-dlq
     */
    @Bean
    public NewTopic fileUpsertEventsDLQTopic() {
        logger.info("定义 Kafka DLQ Topic: {}, 分区数: {}, 副本数: {}",
                kafkaTopicProperties.getUpsertDlqTopicName(),
                kafkaTopicProperties.getDlqPartitions(), // 使用dlq特定的分区数
                kafkaTopicProperties.getDefaultReplicas());
        return TopicBuilder.name(kafkaTopicProperties.getUpsertDlqTopicName())
                .partitions(kafkaTopicProperties.getDlqPartitions())
                .replicas(kafkaTopicProperties.getDefaultReplicas())
                .build();
    }

    /**
     * 定义文件删除事件的 Topic。
     *
     * @return NewTopic bean for dms-file-delete-events
     */
    @Bean
    public NewTopic fileDeleteEventsTopic() {
        logger.info("定义 Kafka Topic: {}, 分区数: {}, 副本数: {}",
                kafkaTopicProperties.getDeleteTopicName(),
                kafkaTopicProperties.getDefaultPartitions(),
                kafkaTopicProperties.getDefaultReplicas());
        return TopicBuilder.name(kafkaTopicProperties.getDeleteTopicName())
                .partitions(kafkaTopicProperties.getDefaultPartitions())
                .replicas(kafkaTopicProperties.getDefaultReplicas())
                .build();
    }

    /**
     * 定义文件删除事件的死信队列 (DLQ) Topic。
     *
     * @return NewTopic bean for dms-file-delete-events-dlq
     */
    @Bean
    public NewTopic fileDeleteEventsDLQTopic() {
        logger.info("定义 Kafka DLQ Topic: {}, 分区数: {}, 副本数: {}",
                kafkaTopicProperties.getDeleteDlqTopicName(),
                kafkaTopicProperties.getDlqPartitions(), // 使用dlq特定的分区数
                kafkaTopicProperties.getDefaultReplicas());
        return TopicBuilder.name(kafkaTopicProperties.getDeleteDlqTopicName())
                .partitions(kafkaTopicProperties.getDlqPartitions())
                .replicas(kafkaTopicProperties.getDefaultReplicas())
                .build();
    }

    // 注意: 如果不希望Spring Boot自动管理KafkaAdminClient的创建，
    // 或者需要更细致地配置KafkaAdmin，可以显式定义一个KafkaAdmin bean。
    // 通常情况下，Spring Boot会自动配置一个KafkaAdmin bean（如果spring-kafka在类路径下）。
    // @Bean
    // public KafkaAdmin kafkaAdmin(KafkaProperties kafkaProperties) {
    //     Map<String, Object> configs = new HashMap<>(kafkaProperties.buildAdminProperties(null));
    //     // 可以添加或覆盖 KafkaAdmin 的配置
    //     // configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "override_server_if_needed");
    //     return new KafkaAdmin(configs);
    // }
}
