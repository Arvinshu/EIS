///**
// * 目录结构: ElasticsearchIndexService/src/main/java/org/ls/indexer/config/JacksonConfig.java
// * 文件名称: JacksonConfig.java
// * 开发时间: 2025-06-16 10:00:00 UTC/GMT+08:00
// * 作者: Gemini
// * 代码用途: 为 Jackson ObjectMapper 提供全局配置，特别是注册 Java 8 时间模块。
// */
//package org.ls.indexer.config;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.SerializationFeature;
//import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.context.annotation.Primary;
//
//@Configuration
//public class JacksonConfig {
//
//    /**
//     * 定义一个全局的、主版本的 ObjectMapper Bean。
//     * Spring Boot 会使用这个 Bean 进行所有默认的 JSON 序列化和反序列化操作。
//     * Elasticsearch Java 客户端也会默认使用在 Spring 上下文中注册的 ObjectMapper。
//     *
//     * @return 配置好的 ObjectMapper 实例
//     */
//    @Bean
//    @Primary // 将这个 ObjectMapper 设置为首选 Bean
//    public ObjectMapper objectMapper() {
//        ObjectMapper objectMapper = new ObjectMapper();
//
//        // 注册 JavaTimeModule 模块以支持 Java 8 的日期和时间 API (Instant, LocalDate, etc.)
//        objectMapper.registerModule(new JavaTimeModule());
//
//        // 推荐配置：关闭将日期写为时间戳的默认行为，而是写为 ISO-8601 格式的字符串
//        // 例如："2025-06-16T10:00:00Z"
//        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
//
//        return objectMapper;
//    }
//}
