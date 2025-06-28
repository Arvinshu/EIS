/**
 * 目录结构: ElasticsearchIndexService/src/main/java/org/ls/indexer/config/properties/IndexerProperties.java
 * 文件名称: IndexerProperties.java
 * 开发时间: 2025-05-19 03:30:00 UTC/GMT+08:00
 * 作者: Gemini
 * 代码用途: 索引器特定配置属性类。
 */
package org.ls.indexer.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Data
@Component
@ConfigurationProperties(prefix = "dms.indexer")
public class IndexerProperties {

    /**
     * 支持索引的文件扩展名列表 (逗号分隔, 例如 .txt,.pdf,.docx).
     * 对应配置文件中的 dms.indexer.supported-extensions
     * application.properties 中已配置: dms.indexer.supported-extensions=.txt,.md,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.vsd,.vsdx
     */
    private String supportedExtensions = ".txt,.pdf,.docx"; // 提供一个默认值以防配置文件未配置

    /**
     * 获取处理过的支持文件扩展名的集合。
     * 扩展名会转换为小写并去除首尾空格。
     *
     * @return 支持的文件扩展名集合 (例如, [".txt", ".pdf"])
     */
    public Set<String> getSupportedExtensionsSet() {
        if (supportedExtensions == null || supportedExtensions.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(supportedExtensions.split(","))
                .map(String::trim)
                .map(String::toLowerCase) // 统一转为小写以便比较
                .filter(s -> !s.isEmpty() && s.startsWith("."))
                .collect(Collectors.toSet());
    }
}
