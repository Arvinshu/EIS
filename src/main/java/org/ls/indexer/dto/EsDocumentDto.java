/**
 * 目录结构: ElasticsearchIndexService/src/main/java/org/ls/indexer/dto/EsDocumentDto.java
 * 文件名称: EsDocumentDto.java
 * 开发时间: 2025-05-19 00:35:10 UTC/GMT+08:00
 * 作者: Gemini
 * 代码用途: 数据传输对象 (DTO)，用于构建要索引到 Elasticsearch 的文档结构。
 */
package org.ls.indexer.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;
import java.util.Map;

/**
 * Elasticsearch 文档的数据传输对象。
 * 代表将要存储在 Elasticsearch "dms_files" 索引中的文档结构。
 *
 * 字段名与 Elasticsearch 索引模板 (dms_files_template.json) 中定义的字段名一致。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder // Lombok Builder模式，方便对象构建
@JsonInclude(JsonInclude.Include.NON_NULL) // 序列化为JSON时，忽略null值的字段
public class EsDocumentDto {

    /**
     * Elasticsearch 文档的 _id，对应 Kafka 消息中的 elasticsearchDocumentId。
     * 在 ES Mapping 中定义为 "file_id"。
     */
    @JsonProperty("file_id")
    private String fileId;

    /**
     * 文件提取的纯文本内容。
     * 在 ES Mapping 中定义为 "content"，使用 IK Analyzer 分词。
     */
    @JsonProperty("content")
    private String content;

    /**
     * 源加密文件名 (原始加密文件名)。
     * 在 ES Mapping 中定义为 "filename"。
     */
    @JsonProperty("filename")
    private String filename;

    /**
     * 源加密文件的完整相对路径 (用于下载链接)。
     * 例如: "documents/projectA/report.docx.enc"
     * 在 ES Mapping 中定义为 "source_path"。
     */
    @JsonProperty("source_path")
    private String sourcePath;

    /**
     * 文件最后修改时间。
     * 将从 Kafka 消息中的 targetFileLastModifiedEpochSeconds (long) 转换而来。
     * Elasticsearch 期望的是日期格式，例如 "yyyy-MM-dd'T'HH:mm:ss'Z'" 或 epoch_second。
     * 我们的索引模板支持 epoch_second。
     * 在 ES Mapping 中定义为 "last_modified"。
     */
    @JsonProperty("last_modified")
    private Long lastModified; // 存储为 epoch_second

    /**
     * Tika 提取的标题。
     * 在 ES Mapping 中定义为 "title"，可使用 IK Analyzer 分词。
     */
    @JsonProperty("title")
    private String title;

    /**
     * Tika 提取的作者。
     * 在 ES Mapping 中定义为 "author"。
     */
    @JsonProperty("author")
    private String author;

    /**
     * 目标文件大小 (字节)。
     * 从 Kafka 消息中的 targetFileSizeBytes 获取。
     * 在 ES Mapping 中定义为 "file_size_bytes"。
     */
    @JsonProperty("file_size_bytes")
    private Long fileSizeBytes;

    /**
     * Kafka 事件时间戳。
     * 可以是 Kafka 消息中的 eventTimestamp。
     * 我们的索引模板支持 strict_date_optional_time_nanos 或 epoch_millis。
     * 在 ES Mapping 中定义为 "event_timestamp"。
     */
    @JsonProperty("event_timestamp")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC") // 示例格式，如果源是Instant
    private Instant eventTimestamp; // 或者使用 String/Long，取决于Kafka消息中的确切格式

    /**
     * 可选，存储 Tika 提取的所有元数据。
     * 在设计文档中提及，但未在核心ES文档结构中强制要求。
     * 如果需要，可以在ES Mapping中定义为 "nested" 或 "object" 类型。
     * private Map<String, Object> extractedMetadata;
     */

    /**
     * 可选，存储来自 Kafka 消息的业务元数据。
     * 对应 FileUpsertEventDto 中的 customMetadata。
     * 如果需要，可以在ES Mapping中定义为 "nested" 或 "object" 类型。
     * private Map<String, Object> customMetadata;
     */

}
