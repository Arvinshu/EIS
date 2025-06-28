/**
 * 目录结构: ElasticsearchIndexService/src/main/java/org/ls/indexer/elasticsearchIndexServiceApplication.java
 * 文件名称: elasticsearchIndexServiceApplication.java
 * 开发时间: 2025-05-18 23:15:05 UTC/GMT+08:00
 * 作者: Gemini
 * 代码用途: Spring Boot 主启动类，用于启动 elasticsearchIndexService 应用。
 */
package org.ls.indexer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync; // 新增导入

/**
 * Elasticsearch索引服务主应用程序类。
 * <p>
 * {@link SpringBootApplication} 注解是一个方便的注解，它添加了以下所有内容：
 * <ul>
 * <li>{@code @Configuration}: 将类标记为应用程序上下文的 bean 定义源。</li>
 * <li>{@code @EnableAutoConfiguration}: 告诉 Spring Boot 根据类路径设置、其他 bean 和各种属性设置开始添加 bean。</li>
 * <li>{@code @ComponentScan}: 告诉 Spring 在 {@code org.ls.indexer} 包中查找其他组件、配置和服务，允许它找到控制器。</li>
 * </ul>
 * {@link ConfigurationPropertiesScan} 注解用于自动扫描并注册 {@code @ConfigurationProperties} 注解的类。
 * </p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan("org.ls.indexer.config.properties") // 指定扫描 @ConfigurationProperties 注解的包路径
@EnableAsync // <--- 添加此注解以启用异步方法执行
@EnableKafka
public class elasticsearchIndexServiceApplication {

    /**
     * 应用程序的主入口点。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(elasticsearchIndexServiceApplication.class, args);
        System.out.println("----------- ElasticsearchIndexService Application Started -----------");
        System.out.println("kafka状态监控页面: http://localhost:8080/indexer-monitor/index.html"); // 假设端口是 8080
    }

}
