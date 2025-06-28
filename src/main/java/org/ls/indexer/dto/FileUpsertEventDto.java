/**
 * 目录结构: ElasticsearchIndexService/src/main/java/org/ls/indexer/dto/FileUpsertEventDto.java
 * 文件名称: FileUpsertEventDto.java
 * 开发时间: 2025-05-19 00:35:00 UTC/GMT+08:00
 * 作者: Gemini
 * 代码用途: 数据传输对象 (DTO)，用于封装文件新增/更新事件的消息。
 */
package org.ls.indexer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Map;

/**
 * 文件新增/更新事件的数据传输对象。
 * 用于从 Kafka Topic "dms-file-upsert-events" 接收和反序列化消息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // 忽略JSON中未知属性，增强兼容性
public class FileUpsertEventDto {

    /**
     * Elasticsearch 文档 ID。
     * 这个ID将用作 ES 文档的 _id。
     */
    @JsonProperty("elasticsearchDocumentId")
    private String elasticsearchDocumentId;

    /**
     * 目标文件在 targetDirectory 中的相对路径。
     * 例如："departmentA/projectX/"
     */
    @JsonProperty("targetRelativePath")
    private String targetRelativePath;

    /**
     * 目标文件的文件名 (解密后的原始文件名)。
     * 例如："annual_report_2023.pdf"
     */
    @JsonProperty("targetFilename")
    private String targetFilename;

    /**
     * 源加密文件名 (用于显示和下载关联)。
     * 例如："annual_report_2023.pdf.enc"
     */
    @JsonProperty("sourceFilename")
    private String sourceFilename;

    /**
     * 源加密文件在其存储位置的相对路径。
     * 例如："encrypted_files/departmentA/projectX/"
     */
    @JsonProperty("sourceRelativePath")
    private String sourceRelativePath;

    /**
     * 目标文件的最后修改时间 (Epoch seconds)。
     * 例如：1678886400
     */
    @JsonProperty("targetFileLastModifiedEpochSeconds")
    private long targetFileLastModifiedEpochSeconds;

    /**
     * 目标文件的大小 (字节)。
     */
    @JsonProperty("targetFileSizeBytes")
    private long targetFileSizeBytes;

    /**
     * Kafka 事件的时间戳 (可选, Epoch millis or nanoseconds as string, depending on producer)。
     * 用于审计。
     */
    @JsonProperty("eventTimestamp")
    private String eventTimestamp; // 或者使用 Long/Instant 类型，并配置Jackson的序列化/反序列化

    /**
     * 其他自定义元数据。
     * 这是一个灵活的字段，可以包含生产者发送的任何额外业务相关信息。
     */
    @JsonProperty("customMetadata")
    private Map<String, Object> customMetadata;

}
