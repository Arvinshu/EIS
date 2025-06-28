/**
 * 目录结构: ElasticsearchIndexService/src/main/java/org/ls/indexer/batch/FileToEsDocumentProcessor.java
 * 文件名称: FileToEsDocumentProcessor.java
 * 开发时间: 2025-05-19 04:00:00 UTC/GMT+08:00
 * 作者: Gemini
 * 代码用途: Spring Batch ItemProcessor，将文件路径转换为EsDocumentDto对象。
 *
 * 功能说明：
 * ItemProcessor<Path, EsDocumentDto>: 实现此接口，表明它接收一个 Path 对象，并输出一个 EsDocumentDto 对象（或者 null 如果该项应被跳过）。
 * 依赖注入: 通过构造函数注入 FileParserService 和 ElasticsearchIdGenerator。这些依赖在 BatchConfig.java 中已经配置好了。
 * process(Path filePath) 方法:
 * 接收一个文件路径。
 * 调用 FileParserService: 使用 fileParserService.parseFile(filePath) 来提取文件的文本内容、标题和作者。
 * 处理解析结果: 如果解析结果为 null 或者提取的内容为空，则记录警告并返回 null。返回 null 会告诉 Spring Batch 跳过这个 Item，它不会被传递给 ItemWriter。
 * 调用 ElasticsearchIdGenerator: 使用 elasticsearchIdGenerator.generateIdFromFilePath(filePath) 为当前文件生成一个确定性的文档 ID。
 * 获取文件属性:
 * 使用 Files.size(filePath) 获取文件大小。
 * 使用 Files.readAttributes(filePath, BasicFileAttributes.class) 获取文件的基本属性，然后从中提取最后修改时间并转换为 epoch seconds。
 * 构建 EsDocumentDto: 使用 EsDocumentDto.builder() 创建文档对象。
 * filename: 对于历史数据批量索引，我们直接使用文件的实际名称。
 * sourcePath: 使用文件的绝对路径作为源路径。
 * eventTimestamp: 对于批量作业，可以将事件时间戳设置为当前处理时间 (Instant.now())。
 * 错误处理:
 * 捕获 FileParserService 可能抛出的 IndexingException。在这种情况下，记录错误并返回 null 以跳过该文件。
 * 捕获其他任何意外的 Exception。在这种情况下，记录错误并重新抛出异常。这允许 Spring Batch 的步骤级错误处理机制（如配置的 skipLimit 或 retryLimit）介入。如果希望这些错误也只是跳过该项，可以改为返回 null。
 *
 */
package org.ls.indexer.batch;

import org.ls.indexer.dto.EsDocumentDto;
import org.ls.indexer.dto.FileParseResult;
import org.ls.indexer.exception.IndexingException;
import org.ls.indexer.service.FileParserService;
import org.ls.indexer.util.ElasticsearchIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor; // Spring Batch ItemProcessor

import java.io.IOException; // For Files.size
import java.nio.file.Files; // For Files.size
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes; // For lastModifiedTime
import java.time.Instant;

public class FileToEsDocumentProcessor implements ItemProcessor<Path, EsDocumentDto> {

    private static final Logger logger = LoggerFactory.getLogger(FileToEsDocumentProcessor.class);

    private final FileParserService fileParserService;
    private final ElasticsearchIdGenerator elasticsearchIdGenerator;

    public FileToEsDocumentProcessor(FileParserService fileParserService,
                                     ElasticsearchIdGenerator elasticsearchIdGenerator) {
        this.fileParserService = fileParserService;
        this.elasticsearchIdGenerator = elasticsearchIdGenerator;
    }

    /**
     * 处理单个文件路径，将其转换为 EsDocumentDto。
     *
     * @param filePath ItemReader 提供过来的文件路径。
     * @return 转换后的 EsDocumentDto 对象；如果文件处理失败或不应被索引，则返回 null。
     * @throws Exception 如果在处理过程中发生不可恢复的错误。
     */
    @Override
    public EsDocumentDto process(Path filePath) throws Exception {
        logger.debug("ItemProcessor 开始处理文件: {}", filePath);

        if (filePath == null) {
            logger.warn("ItemProcessor 接收到 null 文件路径，已跳过。");
            return null;
        }

        try {
            // 1. 解析文件内容和元数据
            FileParseResult parseResult = fileParserService.parseFile(filePath);

            // 如果解析结果为空或内容为空 (根据业务需求决定是否跳过)
            if (parseResult == null || parseResult.getContent() == null || parseResult.getContent().isEmpty()) {
                logger.warn("文件 {} 解析结果为空或内容为空，已跳过。", filePath);
                return null; // 跳过此文件
            }

            // 2. 生成 Elasticsearch 文档 ID
            String documentId = elasticsearchIdGenerator.generateIdFromFilePath(filePath);

            // 3. 获取文件属性 (最后修改时间, 文件大小)
            long fileSize;
            long lastModifiedEpochSeconds;
            try {
                fileSize = Files.size(filePath);
                BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
                lastModifiedEpochSeconds = attrs.lastModifiedTime().toInstant().getEpochSecond();
            } catch (IOException e) {
                logger.error("获取文件 {} 的属性 (大小/最后修改时间) 失败: {}", filePath, e.getMessage(), e);
                // 根据策略决定是否跳过或抛出异常，这里选择跳过
                return null;
            }

            // 4. 构建 EsDocumentDto
            EsDocumentDto esDoc = EsDocumentDto.builder()
                    .fileId(documentId)
                    .content(parseResult.getContent())
                    .filename(filePath.getFileName().toString()) // 对于历史批处理，直接使用文件名
                    .sourcePath(filePath.toAbsolutePath().toString()) // 使用绝对路径作为源路径
                    .lastModified(lastModifiedEpochSeconds)
                    .title(parseResult.getTitle())
                    .author(parseResult.getAuthor())
                    .fileSizeBytes(fileSize)
                    .eventTimestamp(Instant.now()) // 对于批量作业，可以将事件时间戳设置为当前处理时间
                    .build();

            logger.info("文件 {} 处理成功。文档 ID: {}, 标题: '{}'",
                    filePath, esDoc.getFileId(), esDoc.getTitle());
            return esDoc;

        } catch (IndexingException e) {
            // FileParserService 可能会抛出 IndexingException
            logger.error("处理文件 {} 时发生索引异常 (IndexingException): {}", filePath, e.getMessage(), e);
            // 根据需要，可以选择记录错误并跳过 (返回null)，或者让异常传播以使步骤失败
            // 返回 null 会被视为一个被过滤的项，不会传递给 ItemWriter
            return null;
        } catch (Exception e) {
            // 捕获其他意外异常
            logger.error("处理文件 {} 时发生未知错误: {}", filePath, e.getMessage(), e);
            // 抛出异常，让Spring Batch的错误处理机制（如skip/retry listener）来处理
            // 或者根据具体策略返回null
            throw e; // 重新抛出，让批处理框架处理
        }
    }
}
