/**
 * 目录结构: ElasticsearchIndexService/src/main/java/org/ls/indexer/batch/ElasticsearchBulkItemWriter.java
 * 文件名称: ElasticsearchBulkItemWriter.java
 * 开发时间: 2025-05-19 04:15:00 UTC/GMT+08:00
 * 作者: Gemini
 * 代码用途: Spring Batch ItemWriter，将EsDocumentDto列表批量写入Elasticsearch。
 * 这个 ItemWriter 将负责接收由 FileToEsDocumentProcessor 处理后生成的 EsDocumentDto 对象列表（以 Chunk 的形式），
 * 并调用 ElasticsearchPersistenceService 将它们批量写入 Elasticsearch。
 *
 */
package org.ls.indexer.batch;

import org.ls.indexer.dto.EsDocumentDto;
import org.ls.indexer.service.ElasticsearchPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk; // Spring Batch Chunk
import org.springframework.batch.item.ItemWriter; // Spring Batch ItemWriter
import java.util.List; // 确保导入 List

public class ElasticsearchBulkItemWriter implements ItemWriter<EsDocumentDto> {

    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchBulkItemWriter.class);

    private final ElasticsearchPersistenceService elasticsearchPersistenceService;

    public ElasticsearchBulkItemWriter(ElasticsearchPersistenceService elasticsearchPersistenceService) {
        this.elasticsearchPersistenceService = elasticsearchPersistenceService;
    }

    /**
     * 将一批 EsDocumentDto 对象批量写入 Elasticsearch。
     *
     * @param chunk 包含要写入的 EsDocumentDto 对象的块 (Chunk)。
     * Chunk 实现了 List 接口，其getItems()方法返回一个List。
     * @throws Exception 如果写入过程中发生错误。
     */
    @Override
    public void write(Chunk<? extends EsDocumentDto> chunk) throws Exception {
        // 从 Chunk 中获取项目列表
        // Chunk<? extends EsDocumentDto> itemsChunk = chunk; // 这行是多余的
        List<? extends EsDocumentDto> items = chunk.getItems();


        if (items.isEmpty()) {
            logger.debug("ItemWriter 接收到空的项目列表，无需写入。");
            return;
        }

        logger.info("ItemWriter 开始批量写入 {} 个文档到 Elasticsearch。", items.size());

        try {
            // ElasticsearchPersistenceService.bulkIndexDocuments 期望 List<EsDocumentDto>
            // 由于 items 是 List<? extends EsDocumentDto>，我们需要将其转换为 List<EsDocumentDto>。
            // 如果确定 EsDocumentDto 是最终类型且不会有子类传递，可以直接强制类型转换。
            // 或者，如果允许子类且 ElasticsearchPersistenceService 可以处理它们，则转换也是安全的。
            // 为了更明确，可以创建一个新的List，但这会引入额外的开销。
            // 在这种情况下，直接转换通常是可以接受的。
            @SuppressWarnings("unchecked") // 压制非受检转换警告，因为我们期望items中的元素都是EsDocumentDto
            List<EsDocumentDto> dtoList = (List<EsDocumentDto>) items;

            boolean success = elasticsearchPersistenceService.bulkIndexDocuments(dtoList);
            if (success) {
                logger.info("成功批量写入 {} 个文档。", items.size());
            } else {
                logger.warn("批量写入 {} 个文档时发生部分或全部失败 (详见ElasticsearchPersistenceService日志)。", items.size());
                // 根据需求，这里可以抛出异常以使当前块或步骤失败
                // 例如: throw new RuntimeException("批量写入 Elasticsearch 失败，请检查日志。");
                // 如果 ElasticsearchPersistenceService.bulkIndexDocuments 在失败时会抛出异常，
                // 那么这里的判断可能就不需要了，异常会直接传播出去。
                // 当前 ElasticsearchPersistenceService.bulkIndexDocuments 设计为返回boolean并记录错误。
            }
        } catch (Exception e) {
            logger.error("ItemWriter 在批量写入 Elasticsearch 时发生严重错误: {}", e.getMessage(), e);
            // 重新抛出异常，让Spring Batch的错误处理机制（如重试、跳过监听器）来处理
            throw e;
        }
    }
}
