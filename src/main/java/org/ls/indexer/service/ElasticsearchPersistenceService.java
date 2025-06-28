/**
 * 目录结构: ElasticsearchIndexService/src/main/java/org/ls/indexer/service/ElasticsearchPersistenceService.java
 * 文件名称: ElasticsearchPersistenceService.java
 * 开发时间: 2025-05-19 01:30:05 UTC/GMT+08:00
 * 作者: Gemini
 * 代码用途: 负责与 Elasticsearch 集群进行交互，处理文档的索引和删除操作。
 */
package org.ls.indexer.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import org.ls.indexer.config.properties.ElasticsearchProperties;
import org.ls.indexer.dto.EsDocumentDto;
import org.ls.indexer.exception.IndexingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class ElasticsearchPersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchPersistenceService.class);

    private final ElasticsearchClient elasticsearchClient;
    private final ElasticsearchProperties elasticsearchProperties;

    @Autowired
    public ElasticsearchPersistenceService(ElasticsearchClient elasticsearchClient,
                                           ElasticsearchProperties elasticsearchProperties) {
        this.elasticsearchClient = elasticsearchClient;
        this.elasticsearchProperties = elasticsearchProperties;
    }

    /**
     * 将单个文档索引（新增或更新）到 Elasticsearch。
     * 使用文档的 fileId 作为 Elasticsearch 文档的 _id。
     *
     * @param document 要索引的 EsDocumentDto 对象。
     * @throws IndexingException 如果索引操作失败。
     */
    public void indexDocument(EsDocumentDto document) throws IndexingException {
        if (document == null || document.getFileId() == null) {
            logger.warn("尝试索引的文档或文档FileId为空，操作已跳过。");
            throw new IndexingException("要索引的文档或FileId不能为空。");
        }

        String indexName = elasticsearchProperties.getIndexName();
        logger.debug("准备索引文档 ID: {} 到索引: {}", document.getFileId(), indexName);

        try {
            IndexRequest.Builder<EsDocumentDto> requestBuilder = new IndexRequest.Builder<EsDocumentDto>()
                    .index(indexName)
                    .id(document.getFileId())
                    .document(document);

            // 乐观并发控制:
            // 根据设计文档，如果启用了 ifSeqNoEnabled，并且可以获取 if_seq_no 和 if_primary_term，
            // 则应在请求中包含它们。
            // 对于从Kafka消费并直接索引的场景，这些值通常不是立即可用的，除非它们随消息传递或通过预读获取。
            // ES的Index API本身通过指定ID即可实现upsert（存在则更新，不存在则创建），这已满足幂等性。
            // 如果 EsDocumentDto 中包含了 sequenceNumber 和 primaryTerm 字段，可以如下设置：
            // if (elasticsearchProperties.isIfSeqNoEnabled() && document.getSequenceNumber() != null && document.getPrimaryTerm() != null) {
            //    logger.debug("为文档 ID: {} 应用乐观锁, ifSeqNo: {}, ifPrimaryTerm: {}",
            //            document.getFileId(), document.getSequenceNumber(), document.getPrimaryTerm());
            //    requestBuilder.ifSeqNo(document.getSequenceNumber());
            //    requestBuilder.ifPrimaryTerm(document.getPrimaryTerm());
            // }

            IndexResponse response = elasticsearchClient.index(requestBuilder.build());

            logger.info("文档 ID: {} 已成功索引到索引: {}, 版本: {}, 结果: {}",
                    response.id(), response.index(), response.version(), response.result());

            if (response.result() == co.elastic.clients.elasticsearch._types.Result.Created ||
                    response.result() == co.elastic.clients.elasticsearch._types.Result.Updated) {
                // 操作成功
            } else if (response.result() == co.elastic.clients.elasticsearch._types.Result.NoOp) {
                logger.info("文档 ID: {} 索引操作为 NoOp (无变化)。", response.id());
            } else {
                // 其他情况可能表示问题，尽管 IndexResponse 通常不直接抛出业务逻辑错误
                logger.warn("文档 ID: {} 索引操作返回非预期结果: {}", response.id(), response.result());
            }

        } catch (IOException e) {
            logger.error("索引文档 ID: {} 到索引 {} 失败: {}", document.getFileId(), indexName, e.getMessage(), e);
            throw new IndexingException("索引文档 " + document.getFileId() + " 失败", e);
        } catch (Exception e) { // 捕获其他潜在的ES客户端异常
            logger.error("索引文档 ID: {} 时发生非IO异常: {}", document.getFileId(), e.getMessage(), e);
            // 例如 co.elastic.clients.elasticsearch.ElasticsearchException
            throw new IndexingException("索引文档 " + document.getFileId() + " 时发生ES客户端异常", e);
        }
    }

    /**
     * 根据文档 ID 从 Elasticsearch 中删除文档。
     *
     * @param documentId 要删除的文档的 ID。
     * @return 如果文档被成功删除或未找到，则返回 true；如果删除操作失败，则返回 false。
     * @throws IndexingException 如果删除操作因IO或其他ES异常失败。
     */
    public boolean deleteDocument(String documentId) throws IndexingException {
        if (documentId == null || documentId.trim().isEmpty()) {
            logger.warn("尝试删除的文档ID为空，操作已跳过。");
            throw new IndexingException("要删除的文档ID不能为空。");
        }

        String indexName = elasticsearchProperties.getIndexName();
        logger.debug("准备从索引: {} 删除文档 ID: {}", indexName, documentId);

        try {
            DeleteRequest deleteRequest = new DeleteRequest.Builder()
                    .index(indexName)
                    .id(documentId)
                    .build();

            DeleteResponse response = elasticsearchClient.delete(deleteRequest);

            if (response.result() == co.elastic.clients.elasticsearch._types.Result.Deleted) {
                logger.info("文档 ID: {} 已从索引: {} 中成功删除。", documentId, indexName);
                return true;
            } else if (response.result() == co.elastic.clients.elasticsearch._types.Result.NotFound) {
                logger.info("尝试删除的文档 ID: {} 在索引: {} 中未找到。", documentId, indexName);
                return true; // 从业务角度看，目标是确保它不存在，所以NotFound也算成功
            } else {
                logger.warn("删除文档 ID: {} 操作返回非预期结果: {}", documentId, response.result());
                return false;
            }
        } catch (IOException e) {
            logger.error("从索引 {} 删除文档 ID: {} 失败: {}", indexName, documentId, e.getMessage(), e);
            throw new IndexingException("删除文档 " + documentId + " 失败", e);
        } catch (Exception e) {
            logger.error("删除文档 ID: {} 时发生非IO异常: {}", documentId, e.getMessage(), e);
            throw new IndexingException("删除文档 " + documentId + " 时发生ES客户端异常", e);
        }
    }

    /**
     * 批量将文档索引（新增或更新）到 Elasticsearch。
     *
     * @param documents 要批量索引的 EsDocumentDto 对象列表。
     * @return 如果所有操作都成功，则返回 true；如果任何操作失败，则返回 false。
     * @throws IndexingException 如果批量操作因IO或其他ES异常失败。
     */
    public boolean bulkIndexDocuments(List<EsDocumentDto> documents) throws IndexingException {
        if (documents == null || documents.isEmpty()) {
            logger.info("没有文档需要批量索引。");
            return true;
        }

        String indexName = elasticsearchProperties.getIndexName();
        logger.info("准备批量索引 {} 个文档到索引: {}", documents.size(), indexName);

        BulkRequest.Builder br = new BulkRequest.Builder();

        for (EsDocumentDto doc : documents) {
            if (doc == null || doc.getFileId() == null) {
                logger.warn("批量索引中遇到一个文档或其FileId为空，已跳过。");
                continue;
            }
            br.operations(op -> op
                    .index(idx -> idx
                            .index(indexName)
                            .id(doc.getFileId())
                            .document(doc)
                    )
            );
        }

        if (br.build().operations().isEmpty()) {
            logger.info("经过滤后，没有有效文档需要批量索引。");
            return true;
        }

        try {
            BulkResponse result = elasticsearchClient.bulk(br.build());
            boolean allSucceeded = true;

            if (result.errors()) {
                logger.warn("批量索引操作中存在错误。");
                allSucceeded = false;
                for (BulkResponseItem item : result.items()) {
                    if (item.error() != null) {
                        logger.error("批量索引失败 - 文档ID [{}]: 操作类型 [{}], 原因: {}",
                                item.id(), item.operationType(), item.error().reason());
                    }
                }
            } else {
                logger.info("批量索引 {} 个文档成功完成，没有报告错误。", documents.size());
            }

            // 即使 result.errors() 为 false，也建议检查每个 item 的状态
            int successCount = 0;
            for (BulkResponseItem item : result.items()) {
                if (item.error() == null) {
                    successCount++;
                    logger.debug("批量操作成功 - 文档ID [{}], 操作类型 [{}], 状态 [{}]",
                            item.id(), item.operationType(), item.status());
                }
            }
            logger.info("批量索引操作: 成功 {} 个, 总共尝试 {} 个有效文档。", successCount, result.items().size());


            return allSucceeded;

        } catch (IOException e) {
            logger.error("批量索引文档到索引 {} 失败: {}", indexName, e.getMessage(), e);
            throw new IndexingException("批量索引文档失败", e);
        } catch (Exception e) {
            logger.error("批量索引文档时发生非IO异常: {}", e.getMessage(), e);
            throw new IndexingException("批量索引文档时发生ES客户端异常", e);
        }
    }
}
