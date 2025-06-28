/**
 * 目录结构: ElasticsearchIndexService/src/main/java/org/ls/indexer/config/BatchConfig.java
 * 文件名称: BatchConfig.java
 * 开发时间: 2025-05-19 03:30:10 UTC/GMT+08:00
 * 作者: Gemini
 * 代码用途: 配置Spring Batch作业，用于历史文件数据的批量索引。
 */
package org.ls.indexer.config;

import org.ls.indexer.batch.DirectoryScanningItemReader;
import org.ls.indexer.batch.ElasticsearchBulkItemWriter;
import org.ls.indexer.batch.FileToEsDocumentProcessor;
import org.ls.indexer.batch.JobCompletionNotificationListener;
import org.ls.indexer.config.properties.IndexerProperties; // 新增导入
import org.ls.indexer.dto.EsDocumentDto;
import org.ls.indexer.service.ElasticsearchPersistenceService;
import org.ls.indexer.service.FileParserService;
import org.ls.indexer.util.ElasticsearchIdGenerator; // 确保导入
import org.ls.indexer.config.properties.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemStreamReader; // 修改为 ItemStreamReader
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Path;

@Configuration
// @EnableBatchProcessing // Spring Boot 3.x 中, 如果 spring-boot-starter-batch 在类路径中，则此注解通常不再是必需的，
// Batch auto-configuration is enabled by default.
// 但如果遇到问题，可以尝试添加它。
public class BatchConfig {

    private static final Logger logger = LoggerFactory.getLogger(BatchConfig.class);

    @Value("${dms.indexer.batch.historical.chunk-size:100}")
    private int chunkSize;

    @Value("${dms.indexer.batch.task-executor.max-pool-size:10}")
    private int maxPoolSize;

    private final JobRepository jobRepository;

    // 依赖的服务和配置
    private final PlatformTransactionManager transactionManager;
    private final AppProperties appProperties;
    private final IndexerProperties indexerProperties; // 新增注入
    private final FileParserService fileParserService;
    private final ElasticsearchPersistenceService elasticsearchPersistenceService;
    private final ElasticsearchIdGenerator elasticsearchIdGenerator; // 新增注入

    @Autowired
    public BatchConfig(JobRepository jobRepository,
                       PlatformTransactionManager transactionManager,
                       AppProperties appProperties,
                       IndexerProperties indexerProperties, // 新增参数
                       FileParserService fileParserService,
                       ElasticsearchPersistenceService elasticsearchPersistenceService,
                       ElasticsearchIdGenerator elasticsearchIdGenerator) { // 新增参数
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.appProperties = appProperties;
        this.indexerProperties = indexerProperties; // 赋值
        this.fileParserService = fileParserService;
        this.elasticsearchPersistenceService = elasticsearchPersistenceService;
        this.elasticsearchIdGenerator = elasticsearchIdGenerator; // 赋值
        logger.info("BatchConfig 初始化完成。Chunk size: {}", chunkSize);
    }

    /**
     * 定义历史文件索引作业的 ItemReader。
     * 负责扫描目录并读取文件路径。
     *
     * @return DirectoryScanningItemReader 实例
     */
    @Bean
    public ItemStreamReader<Path> directoryScanningItemReader() { // 返回类型改为 ItemStreamReader
        logger.debug("创建 DirectoryScanningItemReader Bean...");
        // 传递 indexerProperties
        return new DirectoryScanningItemReader(appProperties, indexerProperties);
    }

    /**
     * 定义历史文件索引作业的 ItemProcessor。
     * 负责将文件路径转换为 EsDocumentDto。
     *
     * @return FileToEsDocumentProcessor 实例
     */
    @Bean
    public ItemProcessor<Path, EsDocumentDto> fileToEsDocumentProcessor() {
        logger.debug("创建 FileToEsDocumentProcessor Bean...");
        // 注入 elasticsearchIdGenerator
        return new FileToEsDocumentProcessor(fileParserService, elasticsearchIdGenerator);
    }

    /**
     * 定义历史文件索引作业的 ItemWriter。
     * 负责将 EsDocumentDto 批量写入 Elasticsearch。
     *
     * @return ElasticsearchBulkItemWriter 实例
     */
    @Bean
    public ItemWriter<EsDocumentDto> elasticsearchBulkItemWriter() {
        logger.debug("创建 ElasticsearchBulkItemWriter Bean...");
        return new ElasticsearchBulkItemWriter(elasticsearchPersistenceService);
    }

    /**
     * 定义作业完成通知监听器。
     *
     * @return JobCompletionNotificationListener 实例
     */
    @Bean
    public JobCompletionNotificationListener jobCompletionNotificationListener() {
        logger.debug("创建 JobCompletionNotificationListener Bean...");
        return new JobCompletionNotificationListener();
    }

    /**
     * 定义历史文件索引的步骤 (indexHistoricalFilesStep)。
     * 配置 ItemReader, ItemProcessor, ItemWriter 以及事务和分块大小。
     *
     * @param reader    ItemReader 实例
     * @param processor ItemProcessor 实例
     * @param writer    ItemWriter 实例
     * @return Step 实例
     */
    @Bean
    public Step indexHistoricalFilesStep(ItemStreamReader<Path> reader, // 参数类型改为 ItemStreamReader
                                         ItemProcessor<Path, EsDocumentDto> processor,
                                         ItemWriter<EsDocumentDto> writer) {
        logger.debug("构建 indexHistoricalFilesStep Bean...");
        return new StepBuilder("indexHistoricalFilesStep", jobRepository)
                .<Path, EsDocumentDto>chunk(chunkSize, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                // .faultTolerant() // 可选: 配置容错，如跳过某些异常
                // .skipLimit(10)
                // .skip(RuntimeException.class) // 跳过特定类型的异常
                // .taskExecutor(taskExecutor()) // 可选: 为步骤配置多线程执行
                // .throttleLimit(20) // 可选: 限制并发任务数 (如果使用 taskExecutor)
                .build();
    }

    /**
     * 定义历史文件批量索引作业 (historicalFileIndexerJob)。
     *
     * @param indexHistoricalFilesStep      作业要执行的步骤
     * @param listener  作业完成监听器
     * @return Job 实例
     */
    @Bean
    public Job historicalFileIndexerJob(Step indexHistoricalFilesStep,
                                        JobCompletionNotificationListener listener) {
        logger.debug("构建 historicalFileIndexerJob Bean...");
        return new JobBuilder("historicalFileIndexerJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .flow(indexHistoricalFilesStep)
                .end()
                .build();
    }

    /**
     * 可选: 为批处理步骤配置一个任务执行器，以实现多线程处理。
     * 注意: 如果启用，需要确保 ItemReader, ItemProcessor, ItemWriter 是线程安全的，
     * 或者采取适当的同步措施。DirectoryScanningItemReader 可能需要特别注意线程安全。
     *
     * @return TaskExecutor 实例
     */
    @Bean
    public TaskExecutor batchTaskExecutor() {
        SimpleAsyncTaskExecutor asyncTaskExecutor = new SimpleAsyncTaskExecutor("batch-");
        // asyncTaskExecutor.setCorePoolSize(corePoolSize); // SimpleAsyncTaskExecutor 不直接使用 corePoolSize
        // asyncTaskExecutor.setMaxPoolSize(maxPoolSize); // SimpleAsyncTaskExecutor 不直接使用 maxPoolSize
        asyncTaskExecutor.setConcurrencyLimit(maxPoolSize > 0 ? maxPoolSize : SimpleAsyncTaskExecutor.UNBOUNDED_CONCURRENCY);
        // asyncTaskExecutor.setQueueCapacity(queueCapacity); // SimpleAsyncTaskExecutor 不使用队列
        logger.info("配置批处理任务执行器 (SimpleAsyncTaskExecutor) 并发限制为: {}", asyncTaskExecutor.getConcurrencyLimit());
        return asyncTaskExecutor;
    }
}
