/**
 * 目录结构: ElasticsearchIndexService/src/main/java/org/ls/indexer/controller/IndexerStatusController.java
 * 文件名称: IndexerStatusController.java
 * 开发时间: 2025-05-19 05:15:00 UTC/GMT+08:00 (上次编辑时间)
 * 作者: Gemini
 * 代码用途: 提供 REST API 端点来监控服务状态、Kafka、Elasticsearch 及 DLQ。
 */
package org.ls.indexer.controller;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cat.IndicesResponse;
import co.elastic.clients.elasticsearch.cat.indices.IndicesRecord;
import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import co.elastic.clients.elasticsearch.core.CountResponse;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.ls.indexer.config.properties.ElasticsearchProperties;
import org.ls.indexer.config.properties.KafkaTopicProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/status")
public class IndexerStatusController {

    private static final Logger logger = LoggerFactory.getLogger(IndexerStatusController.class);

    private final HealthEndpoint healthEndpoint;
    private final ElasticsearchClient elasticsearchClient;
    private final ElasticsearchProperties elasticsearchProperties;
    private final KafkaAdmin kafkaAdmin;
    private final KafkaTopicProperties kafkaTopicProperties;
    private final KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Autowired
    public IndexerStatusController(HealthEndpoint healthEndpoint,
                                   ElasticsearchClient elasticsearchClient,
                                   ElasticsearchProperties elasticsearchProperties,
                                   KafkaAdmin kafkaAdmin,
                                   KafkaTopicProperties kafkaTopicProperties,
                                   KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry) {
        this.healthEndpoint = healthEndpoint;
        this.elasticsearchClient = elasticsearchClient;
        this.elasticsearchProperties = elasticsearchProperties;
        this.kafkaAdmin = kafkaAdmin;
        this.kafkaTopicProperties = kafkaTopicProperties;
        this.kafkaListenerEndpointRegistry = kafkaListenerEndpointRegistry;
    }

    @GetMapping("/health")
    public ResponseEntity<?> getApplicationHealth() {
        logger.debug("请求应用健康状况。");
        try {
            HealthComponent healthComponent = healthEndpoint.health();
            return ResponseEntity.ok(healthComponent);
        } catch (Exception e) {
            logger.error("获取应用健康状况失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "获取应用健康状况失败", "message", e.getMessage()));
        }
    }

    @GetMapping("/elasticsearch/cluster-health")
    public ResponseEntity<?> getElasticsearchClusterHealth() {
        logger.debug("请求 Elasticsearch 集群健康状况。");
        try {
            HealthResponse healthResponse = elasticsearchClient.cluster().health();
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("clusterName", healthResponse.clusterName());
            responseMap.put("status", healthResponse.status().jsonValue());
            responseMap.put("numberOfNodes", healthResponse.numberOfNodes());
            responseMap.put("numberOfDataNodes", healthResponse.numberOfDataNodes());
            responseMap.put("activePrimaryShards", healthResponse.activePrimaryShards());
            responseMap.put("activeShards", healthResponse.activeShards());
            responseMap.put("relocatingShards", healthResponse.relocatingShards());
            responseMap.put("initializingShards", healthResponse.initializingShards());
            responseMap.put("unassignedShards", healthResponse.unassignedShards());
            return ResponseEntity.ok(responseMap);
        } catch (Exception e) {
            logger.error("获取 Elasticsearch 集群健康状况失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "获取 Elasticsearch 集群健康状况失败", "message", e.getMessage()));
        }
    }

    @GetMapping("/elasticsearch/index-stats/{indexName}")
    public ResponseEntity<?> getElasticsearchIndexStats(@PathVariable String indexName) {
        logger.debug("请求 Elasticsearch 索引 '{}' 的统计信息。", indexName);
        Map<String, Object> responseMap = new HashMap<>();
        try {
            CountResponse countResponse = elasticsearchClient.count(c -> c.index(indexName));
            responseMap.put("documentCount", countResponse.count());

            IndicesResponse catResponse = elasticsearchClient.cat().indices(i -> i.index(indexName));

            if (catResponse.valueBody() != null && !catResponse.valueBody().isEmpty()) {
                IndicesRecord record = catResponse.valueBody().get(0);
                responseMap.put("health", record.health());
                responseMap.put("status", record.status());
                responseMap.put("primaryShards", record.pri());
                responseMap.put("replicaShards", record.rep());
                responseMap.put("docsCountCat", record.docsCount());
                responseMap.put("docsDeletedCat", record.docsDeleted());
                responseMap.put("storeSize", record.storeSize());
                responseMap.put("primaryStoreSize", record.priStoreSize());
            } else {
                responseMap.put("indexInfo", "未从_cat/indices获取到信息或索引不存在。");
            }
            return ResponseEntity.ok(responseMap);
        } catch (Exception e) {
            logger.error("获取 Elasticsearch 索引 '{}' 统计信息失败: {}", indexName, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "获取索引 " + indexName + " 统计信息失败", "message", e.getMessage()));
        }
    }

    @GetMapping("/kafka/dlq-summary")
    public ResponseEntity<?> getDlqSummary() {
        logger.debug("请求 Kafka DLQ 摘要信息。");
        Map<String, Object> responseMap = new HashMap<>();
        List<String> dlqTopics = List.of(
                kafkaTopicProperties.getUpsertDlqTopicName(),
                kafkaTopicProperties.getDeleteDlqTopicName()
        );

        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            for (String dlqTopic : dlqTopics) {
                try {
                    DescribeTopicsResult describeTopicsResult = adminClient.describeTopics(Collections.singletonList(dlqTopic));
                    TopicDescription topicDescription = describeTopicsResult.topicNameValues().get(dlqTopic).get();

                    List<TopicPartition> partitions = topicDescription.partitions().stream()
                            .map(p -> new TopicPartition(dlqTopic, p.partition()))
                            .collect(Collectors.toList());

                    Map<TopicPartition, OffsetSpec> requestOffsets = partitions.stream()
                            .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest()));

                    ListOffsetsResult offsetsResult = adminClient.listOffsets(requestOffsets);
                    long totalMessages = 0;
                    Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> partitionData = offsetsResult.all().get();

                    for (TopicPartition tp : partitions) {
                        try {
                            ListOffsetsResult.ListOffsetsResultInfo info = partitionData.get(tp);
                            if (info != null) {
                                totalMessages += info.offset();
                            } else {
                                logger.warn("在ListOffsetsResult中未找到DLQ Topic '{}' 分区 {} 的偏移量信息。", dlqTopic, tp.partition());
                            }
                        } catch (Exception e) {
                            logger.warn("处理DLQ Topic '{}' 分区 {} 的偏移量时失败: {}", dlqTopic, tp.partition(), e.getMessage());
                        }
                    }
                    responseMap.put(dlqTopic + "_message_count_approx", totalMessages);
                } catch (Exception e) {
                    logger.error("获取DLQ Topic '{}' 信息失败: {}", dlqTopic, e.getMessage(), e);
                    responseMap.put(dlqTopic + "_error", "获取信息失败: " + e.getMessage());
                }
            }
            return ResponseEntity.ok(responseMap);
        } catch (Exception e) {
            logger.error("创建 Kafka AdminClient 或执行DLQ摘要查询失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "获取DLQ摘要信息失败", "message", e.getMessage()));
        }
    }

    @GetMapping("/kafka/consumer-groups/lag")
    public ResponseEntity<?> getConsumerLag() {
        logger.debug("请求 Kafka 消费者 Lag 信息。");
        Map<String, Object> consumerLagInfo = new HashMap<>();

        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            for (String listenerId : kafkaListenerEndpointRegistry.getListenerContainerIds()) {
                MessageListenerContainer container = kafkaListenerEndpointRegistry.getListenerContainer(listenerId);
                if (container != null && container.getGroupId() != null) {
                    String groupId = container.getGroupId();
                    Collection<TopicPartition> assignedPartitions = container.getAssignedPartitions();

                    if (assignedPartitions == null || assignedPartitions.isEmpty()) {
                        logger.debug("消费者组 '{}' (Listener ID: {}) 当前未分配分区。", groupId, listenerId);
                        consumerLagInfo.put(groupId + "_listener_" + listenerId + "_status", "No partitions assigned");
                        continue;
                    }

                    Map<String, Object> groupDetails = new HashMap<>();
                    groupDetails.put("listenerId", listenerId);
                    List<Map<String, Object>> partitionLags = new ArrayList<>();

                    Map<TopicPartition, OffsetAndMetadata> committedOffsets = adminClient
                            .listConsumerGroupOffsets(groupId)
                            .partitionsToOffsetAndMetadata()
                            .get();

                    Map<TopicPartition, OffsetSpec> endOffsetRequest = assignedPartitions.stream()
                            .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest()));
                    Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> endOffsets = adminClient
                            .listOffsets(endOffsetRequest)
                            .all()
                            .get();

                    long totalLagForGroup = 0;
                    for (TopicPartition tp : assignedPartitions) {
                        Map<String, Object> partitionDetail = new HashMap<>();
                        partitionDetail.put("topic", tp.topic());
                        partitionDetail.put("partition", tp.partition());

                        // --- FIX START ---
                        OffsetAndMetadata committedOffsetMetadata = committedOffsets.get(tp);
                        long committed = (committedOffsetMetadata != null) ? committedOffsetMetadata.offset() : -1;
                        // --- FIX END ---

                        ListOffsetsResult.ListOffsetsResultInfo leoInfo = endOffsets.get(tp);
                        long leo = (leoInfo != null) ? leoInfo.offset() : -1;
                        long lag = -1;

                        if (committed != -1 && leo != -1) {
                            lag = Math.max(0, leo - committed);
                            totalLagForGroup += lag;
                        } else if (leo != -1) {
                            // 如果有末尾偏移量但没有已提交偏移量，认为 lag 等于末尾偏移量
                            lag = leo;
                            totalLagForGroup += lag;
                        }

                        partitionDetail.put("committedOffset", committed == -1 ? "N/A" : committed);
                        partitionDetail.put("logEndOffset", leo);
                        partitionDetail.put("lag", lag);
                        partitionLags.add(partitionDetail);
                    }
                    groupDetails.put("totalLag", totalLagForGroup);
                    groupDetails.put("partitions", partitionLags);
                    consumerLagInfo.put("group_" + groupId, groupDetails);
                }
            }
            return ResponseEntity.ok(consumerLagInfo);
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("获取 Kafka 消费者 Lag 信息时发生中断或执行错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "获取消费者 Lag 失败", "message", e.getMessage()));
        } catch (Exception e) {
            logger.error("获取 Kafka 消费者 Lag 信息时发生未知错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "获取消费者 Lag 失败", "message", e.getMessage()));
        }
    }
}