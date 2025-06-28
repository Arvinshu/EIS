/**
 * 目录结构: ElasticsearchIndexService/src/main/java/org/ls/indexer/kafka/FileEventListener.java
 * 文件名称: FileEventListener.java
 * 开发时间: 2025-05-19 02:00:05 UTC/GMT+08:00
 * 作者: Gemini
 * 代码用途: Kafka消息监听器，处理文件变更事件并更新Elasticsearch索引。
 */
package org.ls.indexer.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ls.indexer.config.properties.AppProperties;
import org.ls.indexer.config.properties.KafkaTopicProperties;
import org.ls.indexer.dto.EsDocumentDto;
import org.ls.indexer.dto.FileDeleteEventDto;
import org.ls.indexer.dto.FileParseResult;
import org.ls.indexer.dto.FileUpsertEventDto;
import org.ls.indexer.exception.IndexingException;
import org.ls.indexer.service.ElasticsearchPersistenceService;
import org.ls.indexer.service.FileParserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeParseException;
import org.springframework.util.StringUtils;

@Component
public class FileEventListener {

    private static final Logger logger = LoggerFactory.getLogger(FileEventListener.class);

    private final ObjectMapper objectMapper;
    private final FileParserService fileParserService;
    private final ElasticsearchPersistenceService elasticsearchPersistenceService;
    private final KafkaTopicProperties kafkaTopicProperties;
    private final AppProperties appProperties;

    @Autowired
    public FileEventListener(ObjectMapper objectMapper,
                             FileParserService fileParserService,
                             ElasticsearchPersistenceService elasticsearchPersistenceService,
                             KafkaTopicProperties kafkaTopicProperties,
                             AppProperties appProperties) {
        this.objectMapper = objectMapper; // Spring Boot 会自动配置一个 ObjectMapper bean
        this.fileParserService = fileParserService;
        this.elasticsearchPersistenceService = elasticsearchPersistenceService;
        this.kafkaTopicProperties = kafkaTopicProperties;
        this.appProperties = appProperties;
    }

    /**
     * 监听文件新增/更新事件的 Kafka Topic。
     *
     * @param message    Kafka 消息体 (JSON 字符串)
     * @param topic      消息来源 Topic
     * @param partition  消息来源分区
     * @param offset     消息偏移量
     * @param ack        Acknowledgment 对象，用于手动提交偏移量 (如果配置为手动提交)
     */
    @KafkaListener(
            topics = "#{__listener.kafkaTopicProperties.upsertTopicName}", // 使用SpEL表达式动态获取Topic名称
            groupId = "${spring.kafka.consumer.group-id}", // 从配置文件读取消费者组ID
            containerFactory = "kafkaListenerContainerFactory" // 指定监听器容器工厂, 后续配置
    )
    public void handleFileUpsertEvent(@Payload String message,
                                      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                      @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                      @Header(KafkaHeaders.OFFSET) long offset,
                                      Acknowledgment ack) { // 如果是手动ack，需要这个参数
        logger.info("接收到 Upsert 事件 - Topic: {}, Partition: {}, Offset: {}, Message: {}",
                topic, partition, offset, message);

        try {
            FileUpsertEventDto eventDto = objectMapper.readValue(message, FileUpsertEventDto.class);
            logger.debug("反序列化 Upsert 事件成功: {}", eventDto);

            if (appProperties.getTargetBaseDir() == null || appProperties.getTargetBaseDir().isBlank()) {
                logger.error("目标文件基础目录 (dms.common.target-base-dir) 未配置或为空。");
                throw new IndexingException("目标文件基础目录未配置。");
            }

            // 1. 定位文件
            Path targetFilePath = Paths.get(
                    appProperties.getTargetBaseDir(),
                    eventDto.getTargetRelativePath(),
                    eventDto.getTargetFilename()
            ).normalize(); // normalize() 用于处理路径中的 ".." 等
            logger.info("目标文件物理路径: {}", targetFilePath);

            // 2. 调用 FileParserService 解析文件
            // 在实际场景中，还需要检查文件扩展名是否支持，设计文档中提到过
            // dms.indexer.supported-extensions，这部分逻辑可以在这里或FileParserService前置判断
            FileParseResult parseResult = fileParserService.parseFile(targetFilePath);

            // 3. 构建 EsDocumentDto
            EsDocumentDto esDoc = EsDocumentDto.builder()
                    .fileId(eventDto.getElasticsearchDocumentId())
                    .content(parseResult.getContent())
                    .filename(eventDto.getSourceFilename()) // 使用原始加密文件名
                    .sourcePath(Paths.get(eventDto.getSourceRelativePath(), eventDto.getSourceFilename()).toString())
                    .lastModified(eventDto.getTargetFileLastModifiedEpochSeconds()) // epoch seconds
                    .title(parseResult.getTitle())
                    .author(parseResult.getAuthor())
                    .fileSizeBytes(eventDto.getTargetFileSizeBytes())
                    .build();

            // 原始代码 - 处理 eventTimestamp
//            if (StringUtils.hasText(eventDto.getEventTimestamp())) {
//                try {
//                    // 假设 eventTimestamp 是 epoch millis (long) 的字符串形式
//                    long epochMillis = Long.parseLong(eventDto.getEventTimestamp());
//                    esDoc.setEventTimestamp(Instant.ofEpochMilli(epochMillis));
//                } catch (NumberFormatException e) {
//                    logger.warn("无法将 eventTimestamp '{}' 解析为 long (epoch millis)。尝试其他格式或忽略。",
//                            eventDto.getEventTimestamp(), e);
//                    // 可以尝试更复杂的日期解析逻辑，或根据生产者约定调整
//                }
//            }

            // 截断毫秒（不推荐） - 处理 eventTimestamp
//            if (StringUtils.hasText(eventDto.getEventTimestamp())) {
//                String timestampStr = eventDto.getEventTimestamp();
//                Instant parsedInstant = null;
//
//                // 尝试 1: 解析为纪元毫秒 (epoch millis)
//                try {
//                    long epochMillis = Long.parseLong(timestampStr);
//                    parsedInstant = Instant.ofEpochMilli(epochMillis);
//                } catch (NumberFormatException e) {
//                    // 如果不是数字，则尝试 2: 解析为 ISO 日期时间格式
//                    logger.warn("无法将 eventTimestamp '{}' 解析为 long, 尝试解析为 ISO 日期时间格式。", timestampStr);
//                    try {
//                        parsedInstant = Instant.parse(timestampStr);
//                    } catch (DateTimeParseException e2) {
//                        // 如果两种格式都失败，记录一个更严重的错误
//                        logger.error("无法将 eventTimestamp '{}' 解析为任何支持的格式 (epoch millis 或 ISO-8601)。该字段将被忽略。",
//                                timestampStr, e2);
//                    }
//                }
//
//                // 如果任何一种解析成功，则设置值
//                if (parsedInstant != null) {
//                    // 根据要求，将精度截断到秒
//                    esDoc.setEventTimestamp(parsedInstant.truncatedTo(ChronoUnit.SECONDS));
//                }
//            }

            // 直接解析 ISO 8601 格式（推荐） 处理 eventTimestamp
            if (StringUtils.hasText(eventDto.getEventTimestamp())) {
                try {
                    // 直接解析 ISO 8601 格式的字符串
                    Instant timestamp = Instant.parse(eventDto.getEventTimestamp());
                    esDoc.setEventTimestamp(timestamp);
                } catch (DateTimeParseException e) {
                    // 捕获正确的异常类型 DateTimeParseException
                    logger.warn("无法将 eventTimestamp '{}' 解析为 Instant 对象。请检查格式。",
                            eventDto.getEventTimestamp(), e);
                    // 可以添加其他格式的解析尝试，或忽略
                }
            }


            logger.debug("构建的 ES 文档: {}", esDoc);

            // 4. 调用 ElasticsearchPersistenceService 索引文档
            elasticsearchPersistenceService.indexDocument(esDoc);
            logger.info("文档 ID: {} (来自文件: {}) 已成功处理并发送到 Elasticsearch。",
                    eventDto.getElasticsearchDocumentId(), targetFilePath);

            // 如果配置了手动提交偏移量，则在此处提交
            if (ack != null) {
                ack.acknowledge();
                logger.debug("Kafka 消息偏移量已提交 (Upsert): Topic {}, Partition {}, Offset {}", topic, partition, offset);
            }

        } catch (JsonProcessingException e) {
            logger.error("反序列化 Upsert 事件消息失败: '{}'. 错误: {}", message, e.getMessage(), e);
            // 此处通常意味着消息格式错误，应发送到DLQ。后续通过配置ErrorHandler实现。
            // 如果是手动ack，可以选择不提交，让消息被重新消费或进入DLQ。
        } catch (IndexingException e) {
            logger.error("处理 Upsert 事件 (文件: {}) 失败: {}. 消息: {}",
                    message, e.getMessage(), e.getCause() != null ? e.getCause().getMessage() : "", e);
            // 业务逻辑异常，也应考虑发送到DLQ。
        } catch (Exception e) { // 捕获其他所有意外异常
            logger.error("处理 Upsert 事件消息: '{}' 时发生未知错误: {}", message, e.getMessage(), e);
            // 未知异常，发送到DLQ。
        }
    }

    /**
     * 监听文件删除事件的 Kafka Topic。
     *
     * @param message    Kafka 消息体 (JSON 字符串)
     * @param topic      消息来源 Topic
     * @param partition  消息来源分区
     * @param offset     消息偏移量
     * @param ack        Acknowledgment 对象
     */
    @KafkaListener(
            topics = "#{__listener.kafkaTopicProperties.deleteTopicName}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleFileDeleteEvent(@Payload String message,
                                      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                      @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                      @Header(KafkaHeaders.OFFSET) long offset,
                                      Acknowledgment ack) {
        logger.info("接收到 Delete 事件 - Topic: {}, Partition: {}, Offset: {}, Message: {}",
                topic, partition, offset, message);

        try {
            FileDeleteEventDto eventDto = objectMapper.readValue(message, FileDeleteEventDto.class);
            logger.debug("反序列化 Delete 事件成功: {}", eventDto);

            if (eventDto.getElasticsearchDocumentId() == null || eventDto.getElasticsearchDocumentId().isBlank()) {
                logger.warn("Delete 事件中的 elasticsearchDocumentId 为空，消息无法处理: {}", message);
                // 考虑是否抛出异常或直接忽略
                if (ack != null) { // 如果是手动ack，也提交，避免无限重试坏消息
                    ack.acknowledge();
                }
                return;
            }

            // 调用 ElasticsearchPersistenceService 删除文档
            boolean deleted = elasticsearchPersistenceService.deleteDocument(eventDto.getElasticsearchDocumentId());

            if (deleted) {
                logger.info("文档 ID: {} 已成功从 Elasticsearch 删除 (或未找到)。", eventDto.getElasticsearchDocumentId());
            } else {
                // deleteDocument 方法内部已记录详细日志，这里可以简单记录或根据返回值做进一步处理
                logger.warn("尝试删除文档 ID: {} 可能未完全成功 (详见先前日志)。", eventDto.getElasticsearchDocumentId());
            }

            if (ack != null) {
                ack.acknowledge();
                logger.debug("Kafka 消息偏移量已提交 (Delete): Topic {}, Partition {}, Offset {}", topic, partition, offset);
            }

        } catch (JsonProcessingException e) {
            logger.error("反序列化 Delete 事件消息失败: '{}'. 错误: {}", message, e.getMessage(), e);
            // 发送到DLQ
        } catch (IndexingException e) {
            logger.error("处理 Delete 事件 (文档ID: {}) 失败: {}. 消息: {}",
                    message, e.getMessage(), e.getCause() != null ? e.getCause().getMessage() : "", e);
            // 发送到DLQ
        } catch (Exception e) {
            logger.error("处理 Delete 事件消息: '{}' 时发生未知错误: {}", message, e.getMessage(), e);
            // 发送到DLQ
        }
    }

    /**
     * Spring Expression Language (SpEL) 需要一个Bean来引用其属性。
     * 此方法使得 @KafkaListener 注解可以通过 #{__listener.kafkaTopicProperties...} 访问 kafkaTopicProperties。
     * __listener 是SpEL上下文中此bean的默认名称。
     * @return KafkaTopicProperties 实例
     */
    public KafkaTopicProperties getKafkaTopicProperties() {
        return kafkaTopicProperties;
    }
}
