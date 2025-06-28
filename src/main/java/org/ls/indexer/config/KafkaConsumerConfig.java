/**
 * 目录结构: ElasticsearchIndexService/src/main/java/org/ls/indexer/config/KafkaConsumerConfig.java
 * 文件名称: KafkaConsumerConfig.java
 * 开发时间: 2025-05-19 02:30:00 UTC/GMT+08:00
 * 作者: Gemini
 * 代码用途: 配置Spring Kafka消费者相关的Bean，包括监听器容器工厂和错误处理器。
 */
package org.ls.indexer.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.ls.indexer.config.properties.KafkaTopicProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.KafkaException.Level;  //修改日志引用
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff; // 或者 ExponentialBackOff

import java.util.function.BiFunction;

@Configuration
public class KafkaConsumerConfig {

    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    private final KafkaTopicProperties kafkaTopicProperties;
    private final KafkaTemplate<String, String> kafkaTemplate; // 用于DLQ

    @Value("${dms.indexer.kafka.consumer.retry.max-attempts:3}") // 从配置文件读取，默认3次
    private int maxRetryAttempts;

    @Value("${dms.indexer.kafka.consumer.retry.backoff-interval:5000}") // 从配置文件读取，默认5秒 (ms)
    private long backoffInterval;

    @Value("${dms.indexer.kafka.consumer.concurrency:3}") // 从配置文件读取，默认并发数为3
    private int consumerConcurrency;


    @Autowired
    public KafkaConsumerConfig(KafkaTopicProperties kafkaTopicProperties,
                               KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTopicProperties = kafkaTopicProperties;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * 配置 Kafka 监听器容器工厂。
     * 这个工厂将用于创建 FileEventListener 中 @KafkaListener 注解的消费者容器。
     *
     * @param consumerFactory Spring Boot 自动配置的消费者工厂
     * @return ConcurrentKafkaListenerContainerFactory 实例
     */
    @Bean("kafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        // 1. 配置错误处理器 (包括重试和DLQ)
        factory.setCommonErrorHandler(kafkaErrorHandler());

        // 2. 配置手动提交偏移量
        // FileEventListener 中使用了 Acknowledgment 参数，因此这里需要设置为手动提交模式。
        factory.getContainerProperties().setAckMode(AckMode.MANUAL_IMMEDIATE);
        logger.info("Kafka消费者偏移量提交模式设置为: MANUAL_IMMEDIATE");

        // 3. 配置并发消费者数量
        factory.setConcurrency(consumerConcurrency);
        logger.info("Kafka消费者并发数设置为: {}", consumerConcurrency);

        // 4. 批量监听器 (如果需要，当前设计为单条消息处理)
        // factory.setBatchListener(false); // 默认为false，处理单条消息

        // 5. 其他配置，例如消息转换器 (如果DTO直接作为@Payload参数类型，而不是String)
        // 如果直接消费DTO对象，需要配置JsonMessageConverter和对应的TypeMapper
        // factory.setMessageConverter(new StringJsonMessageConverter());

        return factory;
    }

    /**
     * 创建并配置 Kafka 错误处理器。
     * 使用 DefaultErrorHandler 实现重试逻辑，并在重试耗尽后将消息发送到DLQ。
     *
     * @return DefaultErrorHandler 实例
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {
        // DLQ 恢复器: 当重试耗尽时，将消息发送到指定的DLQ Topic
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate, // 使用KafkaTemplate发送到DLQ
                // 为每个原始Topic动态决定其对应的DLQ Topic
                (consumerRecord, exception) -> {
                    logger.warn("消息处理失败，将发送到DLQ。原始Topic: {}, 异常: {}",
                            consumerRecord.topic(), exception.getMessage());
                    if (consumerRecord.topic().equals(kafkaTopicProperties.getUpsertTopicName())) {
                        return new TopicPartition(kafkaTopicProperties.getUpsertDlqTopicName(), consumerRecord.partition());
                    } else if (consumerRecord.topic().equals(kafkaTopicProperties.getDeleteTopicName())) {
                        return new TopicPartition(kafkaTopicProperties.getDeleteDlqTopicName(), consumerRecord.partition());
                    }
                    // 如果有其他Topic，或者作为默认回退
                    logger.error("无法确定消息来源Topic '{}' 的DLQ Topic，将使用默认DLQ或丢弃 (取决于配置)。", consumerRecord.topic());
                    // 可以配置一个默认的全局DLQ Topic，或者返回null导致消息被丢弃或根据其他策略处理
                    return new TopicPartition("unknown-source-topic-dlq", consumerRecord.partition());
                }
        );

        // 添加原始消息头到DLQ消息 (可选，但推荐用于诊断)
        recoverer.setHeadersFunction((consumerRecord, exception) -> {
            // KafkaHeaders 类中定义了一些标准头名称
            // org.springframework.kafka.support.KafkaHeaders
            // 例如: KafkaHeaders.DLT_ORIGINAL_TOPIC, KafkaHeaders.DLT_EXCEPTION_MESSAGE 等
            // DeadLetterPublishingRecoverer 默认会添加一些有用的头信息
            // 如果需要自定义，可以像下面这样添加或覆盖
            // return new RecordHeaders().add(new RecordHeader("my-custom-error-header", exception.getMessage().getBytes()));
            return consumerRecord.headers(); // 保留原始消息头，DLT会自动添加额外错误信息头
        });


        // 配置重试机制 (例如，固定间隔重试)
        // FixedBackOff(interval, maxAttempts)
        // maxAttempts 参数指的是在首次尝试失败后的额外尝试次数。
        // 所以，总的尝试次数是 maxAttempts + 1。
        // DefaultErrorHandler 的构造函数中的 maxAttempts 是总尝试次数。
        // 如果 FixedBackOff 的 maxAttempts 设置为 Long.MAX_VALUE，则由 DefaultErrorHandler 的 attempts 控制。
        FixedBackOff backOff = new FixedBackOff(backoffInterval, maxRetryAttempts -1); // maxRetryAttempts 是总次数
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        // errorHandler.addRetryableExceptions(IndexingException.class, SocketTimeoutException.class);
        // errorHandler.addNotRetryableExceptions(JsonProcessingException.class, NullPointerException.class);
        // 默认情况下，所有异常都会被重试，除非它们是 Spring AMQP 的 AmqpRejectAndDontRequeueException 的实例。
        // 对于反序列化异常 (如 JsonProcessingException)，通常不应重试，因为它们是持久性问题。
        // Spring Kafka 2.8+ 的 DefaultErrorHandler 对于 DeserializationException 默认不会重试，会直接进入 recoverer。
        // 如果 ConsumerFactory 中配置了 ErrorHandlingDeserializer，它可以将反序列化错误包装起来，
        // 使得 DefaultErrorHandler 可以根据包装的异常类型来决定是否重试。

        errorHandler.setLogLevel(Level.WARN);  // 设置重试时的日志级别
        logger.info("Kafka错误处理器配置完成: 重试次数 {}, 退避间隔 {}ms", maxRetryAttempts, backoffInterval);
        return errorHandler;
    }

    // 如果你需要为DLQ专门配置一个KafkaTemplate，可以取消下面的注释并进行配置。
    // Spring Boot通常会自动配置一个 KafkaTemplate<Object, Object>。
    // 为了DLQ发送String消息，一个 KafkaTemplate<String, String> 更合适。
    // 如果项目中没有其他地方定义 KafkaTemplate<String, String>，可以如下定义：
    // @Bean
    // public KafkaTemplate<String, String> stringKafkaTemplate(ProducerFactory<String, String> pf) {
    //     return new KafkaTemplate<>(pf);
    // }
    // 注意：上面的构造函数注入的 kafkaTemplate 应该就是这个类型，或者由Spring Boot自动配置。
    // 确保 application.properties 中有 spring.kafka.producer.key-serializer 和 value-serializer 的配置
    // 例如:
    // spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
    // spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer

    // 关于 ErrorHandlingDeserializer:
    // 如果希望对反序列化失败进行更细致的控制（例如，将其直接发送到DLQ而不重试），
    // 可以在 ConsumerFactory 中配置 ErrorHandlingDeserializer。
    // @Bean
    // public ConsumerFactory<String, String> consumerFactory(KafkaProperties properties) {
    //     Map<String, Object> consumerProps = properties.buildConsumerProperties(null);
    //     // 配置值反序列化器为 ErrorHandlingDeserializer，它包装了实际的 JsonDeserializer
    //     consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class.getName());
    //     // 配置 ErrorHandlingDeserializer 的委托（实际的）反序列化器
    //     consumerProps.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());
    //     // 配置 JsonDeserializer 的目标类型 (如果不是泛型，或者需要特定类型)
    //     // consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "com.example.MyDto");
    //     consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*"); // 信任所有包，或指定具体包
    //
    //     return new DefaultKafkaConsumerFactory<>(consumerProps);
    // }
    // 如果使用这种方式，那么 @KafkaListener 中的 @Payload String message 仍然是字符串，
    // 但如果反序列化失败，ErrorHandlingDeserializer 会捕获它，
    // DefaultErrorHandler 可以识别出这是一个反序列化错误并决定是否重试。
    // 对于我们的场景，FileEventListener 直接消费 String 然后手动用 ObjectMapper 解析，
    // 所以 JsonProcessingException 会在监听器方法内部被捕获。
}
