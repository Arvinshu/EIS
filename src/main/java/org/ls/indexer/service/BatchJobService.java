/**
 * 目录结构: ElasticsearchIndexService/src/main/java/org/ls/indexer/service/BatchJobService.java
 * 文件名称: BatchJobService.java
 * 开发时间: 2025-05-19 04:30:05 UTC/GMT+08:00
 * 作者: Gemini
 * 代码用途: 提供启动和监控Spring Batch作业的服务。
 */
package org.ls.indexer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async; // 用于异步执行作业
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BatchJobService {

    private static final Logger logger = LoggerFactory.getLogger(BatchJobService.class);

    private final JobLauncher jobLauncher;
    private final JobExplorer jobExplorer; // 用于查询作业执行历史和状态
    private final Job historicalFileIndexerJob; // 注入在BatchConfig中定义的作业Bean

    @Autowired
    public BatchJobService(JobLauncher jobLauncher,
                           JobExplorer jobExplorer,
                           @Qualifier("historicalFileIndexerJob") Job historicalFileIndexerJob) {
        this.jobLauncher = jobLauncher;
        this.jobExplorer = jobExplorer;
        this.historicalFileIndexerJob = historicalFileIndexerJob;
    }

    /**
     * 异步启动历史文件索引作业。
     * 使用当前时间戳作为作业参数，以确保每次启动都是一个新的作业实例 (结合RunIdIncrementer)。
     *
     * @return 启动的 JobExecution 对象，如果启动失败则可能返回null或抛出异常。
     * @throws JobInstanceAlreadyCompleteException 如果具有相同参数的作业实例已成功完成且不允许重启。
     * @throws JobExecutionAlreadyRunningException 如果具有相同参数的作业实例已在运行。
     * @throws JobParametersInvalidException 如果提供的参数无效。
     * @throws JobRestartException 如果作业已成功完成且不允许重启。
     */
    @Async // 使用@Async注解使作业启动在单独的线程中异步执行
    public JobExecution startHistoricalFileIndexerJob() throws JobInstanceAlreadyCompleteException,
            JobExecutionAlreadyRunningException, JobParametersInvalidException, JobRestartException {

        logger.info("请求启动历史文件索引作业 (historicalFileIndexerJob)...");
        JobParameters jobParameters = new JobParametersBuilder()
                .addDate("launchDate", new Date()) // 添加时间戳参数确保作业实例唯一性
                // .addString("targetDirectoryOverride", "/custom/path") // 可选：允许覆盖目标目录
                .toJobParameters();

        try {
            JobExecution jobExecution = jobLauncher.run(historicalFileIndexerJob, jobParameters);
            logger.info("历史文件索引作业已启动。JobExecution ID: {}, Status: {}",
                    jobExecution.getId(), jobExecution.getStatus());
            return jobExecution;
        } catch (JobExecutionAlreadyRunningException e) {
            logger.warn("历史文件索引作业已在运行: {}", e.getMessage());
            throw e;
        } catch (JobRestartException e) {
            logger.warn("历史文件索引作业不允许重启: {}", e.getMessage());
            throw e;
        } catch (JobInstanceAlreadyCompleteException e) {
            logger.warn("历史文件索引作业已成功完成且不允许重启: {}", e.getMessage());
            throw e;
        } catch (JobParametersInvalidException e) {
            logger.error("启动历史文件索引作业时参数无效: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) { // 捕获其他可能的启动时异常
            logger.error("启动历史文件索引作业时发生未知错误: {}", e.getMessage(), e);
            throw new RuntimeException("启动批处理作业失败", e);
        }
    }

    /**
     * 根据作业执行ID获取作业执行状态。
     *
     * @param jobExecutionId 作业执行ID
     * @return JobExecution 对象，如果未找到则返回 null。
     */
    public JobExecution getJobExecutionStatus(Long jobExecutionId) {
        if (jobExecutionId == null) {
            return null;
        }
        logger.debug("查询作业执行状态，ID: {}", jobExecutionId);
        return jobExplorer.getJobExecution(jobExecutionId);
    }

    /**
     * 获取名为 "historicalFileIndexerJob" 的作业的最新（或正在运行的）的10个作业实例的执行情况。
     *
     * @return JobExecution 列表，按创建时间降序排列。
     */
    public List<JobExecution> getLatestJobExecutions() {
        String jobName = historicalFileIndexerJob.getName();
        logger.debug("查询作业 '{}' 的最新执行情况...", jobName);

        // 获取此作业的所有实例ID
        List<Long> jobInstanceIds = jobExplorer.getJobInstances(jobName, 0, 100) // 获取最近100个实例
                .stream()
                .map(jobInstance -> jobInstance.getInstanceId())
                .collect(Collectors.toList());

        if (jobInstanceIds.isEmpty()) {
            return List.of();
        }

        // 对于每个实例，获取其所有的执行
        return jobInstanceIds.stream()
                .flatMap(instanceId -> jobExplorer.getJobExecutions(jobExplorer.getJobInstance(instanceId)).stream())
                .sorted((je1, je2) -> je2.getCreateTime().compareTo(je1.getCreateTime())) // 按创建时间降序
                .limit(10) // 获取最新的10个执行
                .collect(Collectors.toList());
    }

    /**
     * 获取所有已知的作业名称。
     *
     * @return 作业名称集合
     */
    public Set<String> getAllJobNames() {
        return jobExplorer.getJobNames().stream().collect(Collectors.toSet());
    }

    // 注意: 为了使 @Async 注解生效，需要在应用主类或一个配置类上添加 @EnableAsync 注解。
    // 例如，在 ElasticsearchIndexServiceApplication.java 上添加 @EnableAsync。
}
