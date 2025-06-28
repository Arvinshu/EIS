/**
 * 目录结构: ElasticsearchIndexService/src/main/java/org/ls/indexer/dto/FileDeleteEventDto.java
 * 文件名称: FileDeleteEventDto.java
 * 开发时间: 2025-05-19 00:35:05 UTC/GMT+08:00
 * 作者: Gemini
 * 代码用途: 数据传输对象 (DTO)，用于封装文件删除事件的消息。
 */
package org.ls.indexer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 文件删除事件的数据传输对象。
 * 用于从 Kafka Topic "dms-file-delete-events" 接收和反序列化消息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FileDeleteEventDto {

    /**
     * 要从 Elasticsearch 中删除的文档 ID。
     * 这个ID对应 ES 文档的 _id。
     */
    @JsonProperty("elasticsearchDocumentId")
    private String elasticsearchDocumentId;

    /**
     * Kafka 事件的时间戳 (可选, Epoch millis or nanoseconds as string)。
     * 用于审计。
     */
    @JsonProperty("eventTimestamp")
    private String eventTimestamp; // 或者使用 Long/Instant 类型

}
