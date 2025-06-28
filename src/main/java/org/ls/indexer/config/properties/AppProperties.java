/**
 * 目录结构: ElasticsearchIndexService/src/main/java/org/ls/indexer/config/properties/AppProperties.java
 * 文件名称: AppProperties.java
 * 开发时间: 2025-05-19 02:00:00 UTC/GMT+08:00
 * 作者: Gemini
 * 代码用途: 应用程序级别的通用配置属性。
 */
package org.ls.indexer.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "dms.common") // 对应配置文件中的 dms.common 前缀
public class AppProperties {

    /**
     * 目标文件基础目录。
     * Kafka消息中的 targetRelativePath 是基于此目录的。
     * 对应配置文件中的 dms.common.target-base-dir
     */
    private String targetBaseDir;

}
