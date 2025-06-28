/**
 * 目录结构: ElasticsearchIndexService/src/main/java/org/ls/indexer/batch/DirectoryScanningItemReader.java
 * 文件名称: DirectoryScanningItemReader.java
 * 开发时间: 2025-05-19 03:30:15 UTC/GMT+08:00
 * 作者: Gemini
 * 代码用途: Spring Batch ItemReader，用于递归扫描指定目录，读取符合条件的文件路径。
 */
package org.ls.indexer.batch;

import org.ls.indexer.config.properties.AppProperties;
import org.ls.indexer.config.properties.IndexerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader; // 更改为 ItemStreamReader
import org.springframework.batch.item.NonTransientResourceException;
import org.springframework.batch.item.ParseException;
import org.springframework.batch.item.UnexpectedInputException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DirectoryScanningItemReader implements ItemStreamReader<Path> {

    private static final Logger logger = LoggerFactory.getLogger(DirectoryScanningItemReader.class);

    private final AppProperties appProperties;
    private final IndexerProperties indexerProperties;

    private List<Path> filePaths;
    private AtomicInteger currentIndex; // 使用 AtomicInteger 保证线程安全，尽管当前版本主要在单线程open中初始化

    private static final String CURRENT_INDEX_KEY = "directory.scan.current.index";

    public DirectoryScanningItemReader(AppProperties appProperties, IndexerProperties indexerProperties) {
        this.appProperties = appProperties;
        this.indexerProperties = indexerProperties;
        this.filePaths = new CopyOnWriteArrayList<>(); // 线程安全的List，以防未来扩展
        this.currentIndex = new AtomicInteger(0);
    }

    /**
     * 在步骤开始前调用，用于打开资源或初始化状态。
     * 此处用于扫描目录并填充文件列表。
     *
     * @param executionContext 用于在批处理运行之间共享和持久化状态。
     * @throws ItemStreamException 如果打开资源失败。
     */
    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        this.filePaths.clear();
        this.currentIndex.set(0);

        String baseDirString = appProperties.getTargetBaseDir();
        if (baseDirString == null || baseDirString.isBlank()) {
            logger.error("目标文件基础目录 (dms.common.target-base-dir) 未配置或为空。无法扫描文件。");
            // 可以选择抛出异常使作业失败，或允许其继续但不会读取任何内容
            // throw new ItemStreamException("目标文件基础目录未配置。");
            return; // 或者返回，让read()返回null
        }

        Path baseDir = Paths.get(baseDirString);
        if (!Files.exists(baseDir) || !Files.isDirectory(baseDir)) {
            logger.warn("目标基础目录 {} 不存在或不是一个目录。ItemReader 将不会读取任何文件。", baseDir);
            return;
        }

        Set<String> supportedExtensions = indexerProperties.getSupportedExtensionsSet();
        if (supportedExtensions.isEmpty()) {
            logger.warn("支持的文件扩展名列表为空。ItemReader 将不会读取任何文件。");
            return;
        }

        logger.info("开始扫描目录: {}，支持的扩展名: {}", baseDir, supportedExtensions);

        try (Stream<Path> pathStream = Files.walk(baseDir)) {
            this.filePaths.addAll(pathStream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String fileName = path.getFileName().toString().toLowerCase();
                        return supportedExtensions.stream().anyMatch(fileName::endsWith);
                    })
                    .collect(Collectors.toList()));
            logger.info("目录扫描完成。发现 {} 个符合条件的文件。", this.filePaths.size());

            // 尝试从 ExecutionContext 恢复索引，实现基本的重启能力
            // 注意：这仅在文件列表本身在重启间保持一致（或重新扫描得到相同顺序）时才有效。
            if (executionContext.containsKey(CURRENT_INDEX_KEY)) {
                this.currentIndex.set(executionContext.getInt(CURRENT_INDEX_KEY));
                logger.info("从 ExecutionContext 恢复读取索引到: {}", this.currentIndex.get());
            }

        } catch (IOException e) {
            logger.error("扫描目录 {} 时发生IO错误: {}", baseDir, e.getMessage(), e);
            throw new ItemStreamException("扫描目录失败: " + baseDir, e);
        }
    }

    /**
     * 从文件列表中读取下一个文件路径。
     *
     * @return 下一个文件路径，如果所有文件已读取完毕则返回 null。
     * @throws Exception 如果读取过程中发生不可恢复的错误。
     */
    @Override
    public Path read() throws Exception, UnexpectedInputException, ParseException, NonTransientResourceException {
        if (currentIndex.get() < filePaths.size()) {
            Path filePath = filePaths.get(currentIndex.getAndIncrement());
            logger.debug("ItemReader 读取文件: {}", filePath);
            return filePath;
        } else {
            logger.info("ItemReader 已读取所有文件，返回 null。");
            return null; // 表示读取结束
        }
    }

    /**
     * 在步骤处理期间定期调用（例如，在每个块之后），用于更新持久化状态。
     *
     * @param executionContext 用于存储状态。
     * @throws ItemStreamException 如果更新状态失败。
     */
    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        // 存储当前读取的索引，以便在作业重启时可以从该点继续（如果文件列表不变）
        executionContext.putInt(CURRENT_INDEX_KEY, currentIndex.get());
        logger.trace("ItemReader 更新 ExecutionContext 中的索引为: {}", currentIndex.get());
    }

    /**
     * 在步骤结束时调用，用于关闭和清理资源。
     *
     * @throws ItemStreamException 如果关闭资源失败。
     */
    @Override
    public void close() throws ItemStreamException {
        logger.info("关闭 DirectoryScanningItemReader。清空文件列表。");
        this.filePaths.clear();
        this.currentIndex.set(0);
    }
}
