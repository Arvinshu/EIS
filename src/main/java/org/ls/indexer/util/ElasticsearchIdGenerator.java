/**
 * 目录结构: ElasticsearchIndexService/src/main/java/org/ls/indexer/util/ElasticsearchIdGenerator.java
 * 文件名称: ElasticsearchIdGenerator.java
 * 开发时间: 2025-05-19 03:30:05 UTC/GMT+08:00
 * 作者: Gemini
 * 代码用途: 生成 Elasticsearch 文档 ID 的工具类。
 */
package org.ls.indexer.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Component // 注册为Spring Bean，方便注入
public class ElasticsearchIdGenerator {

    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchIdGenerator.class);

    /**
     * 根据文件的绝对路径生成一个确定性的 Elasticsearch 文档 ID。
     * 使用文件绝对路径的 SHA-256 哈希值作为 ID，确保对于同一文件路径，ID 总是相同的。
     * 这种方法对于历史数据批量索引非常有用，可以保证幂等性。
     *
     * @param filePath 文件的路径对象
     * @return 生成的 Elasticsearch 文档 ID (SHA-256 Hex String)
     */
    public String generateIdFromFilePath(Path filePath) {
        if (filePath == null) {
            logger.warn("尝试为 null 文件路径生成ID，将回退到UUID。");
            return generateGenericId(); // 或者抛出异常，取决于业务需求
        }
        try {
            // 使用文件的绝对路径以确保唯一性
            String pathToHash = filePath.toAbsolutePath().toString();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(pathToHash.getBytes(StandardCharsets.UTF_8));

            // 将 byte 数组转换为十六进制字符串
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            logger.debug("为文件路径 '{}' 生成的 ID: {}", pathToHash, hexString.toString());
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 应该是标准算法，不太可能发生此异常
            logger.error("SHA-256 算法未找到，回退到UUID生成ID。", e);
            return generateGenericId();
        }
    }

    /**
     * 生成一个通用的唯一ID (基于UUID)。
     * 当无法根据文件路径生成ID时，或对于非文件相关的ID生成场景，可以使用此方法。
     *
     * @return 一个UUID字符串
     */
    public String generateGenericId() {
        String uuid = UUID.randomUUID().toString();
        logger.debug("生成通用 UUID: {}", uuid);
        return uuid;
    }
}
