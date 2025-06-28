/**
 * 目录结构: ElasticsearchIndexService/src/main/java/org/ls/indexer/controller/BatchJobController.java
 * 文件名称: BatchJobController.java
 * 开发时间: 2025-05-19 04:45:00 UTC/GMT+08:00
 * 作者: Gemini
 * 代码用途: 提供 REST API 端点来管理和监控 Spring Batch 作业。
 */
package org.ls.indexer.controller;

import org.ls.indexer.service.BatchJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/batch/historical-index") // API 基础路径
public class BatchJobController {

    private static final Logger logger = LoggerFactory.getLogger(BatchJobController.class);

    private final BatchJobService batchJobService;

    @Autowired
    public BatchJobController(BatchJobService batchJobService) {
        this.batchJobService = batchJobService;
    }

    /**
     * POST /api/batch/historical-index/start
     * 启动历史数据批量索引作业。
     *
     * @return ResponseEntity 包含作业启动信息或错误信息。
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startHistoricalIndexJob() {
        logger.info("接收到启动历史数据索引作业的API请求。");
        Map<String, Object> response = new HashMap<>();
        try {
            JobExecution jobExecution = batchJobService.startHistoricalFileIndexerJob();
            response.put("message", "历史数据索引作业已成功请求启动。");
            response.put("jobExecutionId", jobExecution.getId());
            response.put("status", jobExecution.getStatus().toString());
            // 注意: 由于作业是异步启动的，这里的状态通常是 STARTING 或 STARTED。
            // 客户端需要后续轮询状态。
            return ResponseEntity.ok(response);
        } catch (JobExecutionAlreadyRunningException e) {
            logger.warn("作业启动失败: 作业已在运行。", e);
            response.put("error", "作业已在运行。");
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        } catch (JobInstanceAlreadyCompleteException e) {
            logger.warn("作业启动失败: 作业实例已成功完成。", e);
            response.put("error", "作业实例已成功完成且不允许重启。");
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (JobRestartException e) {
            logger.warn("作业启动失败: 作业不允许重启。", e);
            response.put("error", "作业不允许重启。");
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (JobParametersInvalidException e) {
            logger.error("作业启动失败: 作业参数无效。", e);
            response.put("error", "作业参数无效。");
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (Exception e) {
            logger.error("启动历史数据索引作业时发生未知错误。", e);
            response.put("error", "启动作业时发生内部服务器错误。");
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * GET /api/batch/historical-index/status/{jobExecutionId}
     * 查询特定作业执行的状态。
     *
     * @param jobExecutionId 要查询的作业执行ID。
     * @return ResponseEntity 包含作业执行的详细信息或错误信息。
     */
    @GetMapping("/status/{jobExecutionId}")
    public ResponseEntity<?> getJobStatus(@PathVariable Long jobExecutionId) {
        logger.debug("接收到查询作业执行状态的API请求，JobExecutionId: {}", jobExecutionId);
        JobExecution jobExecution = batchJobService.getJobExecutionStatus(jobExecutionId);

        if (jobExecution == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "未找到指定的作业执行。");
            response.put("jobExecutionId", jobExecutionId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
        return ResponseEntity.ok(formatJobExecution(jobExecution));
    }

    /**
     * GET /api/batch/historical-index/latest-status
     * 查询最新（或正在运行）的历史数据索引作业的执行状态列表。
     *
     * @return ResponseEntity 包含作业执行列表。
     */
    @GetMapping("/latest-status")
    public ResponseEntity<List<Map<String, Object>>> getLatestJobStatuses() {
        logger.debug("接收到查询最新作业执行状态列表的API请求。");
        List<JobExecution> jobExecutions = batchJobService.getLatestJobExecutions();
        List<Map<String, Object>> responseList = jobExecutions.stream()
                .map(this::formatJobExecution)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responseList);
    }

    /**
     * 辅助方法，将 JobExecution 对象格式化为 Map 以便API响应。
     * @param jobExecution JobExecution 对象
     * @return 包含关键信息的 Map
     */
    private Map<String, Object> formatJobExecution(JobExecution jobExecution) {
        Map<String, Object> execDetails = new HashMap<>();
        execDetails.put("jobExecutionId", jobExecution.getId());
        execDetails.put("jobInstanceId", jobExecution.getJobInstance().getInstanceId());
        execDetails.put("jobName", jobExecution.getJobInstance().getJobName());
        execDetails.put("status", jobExecution.getStatus().toString());
        execDetails.put("startTime", jobExecution.getStartTime());
        execDetails.put("endTime", jobExecution.getEndTime());
        execDetails.put("createTime", jobExecution.getCreateTime());
        execDetails.put("lastUpdated", jobExecution.getLastUpdated());
        execDetails.put("exitStatus", jobExecution.getExitStatus().getExitCode());
        execDetails.put("jobParameters", jobExecution.getJobParameters().getParameters().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString()))); //简化参数显示

        // 添加步骤执行信息
        execDetails.put("stepExecutions", jobExecution.getStepExecutions().stream().map(stepExecution -> {
            Map<String, Object> stepDetails = new HashMap<>();
            stepDetails.put("stepName", stepExecution.getStepName());
            stepDetails.put("status", stepExecution.getStatus().toString());
            stepDetails.put("readCount", stepExecution.getReadCount());
            stepDetails.put("writeCount", stepExecution.getWriteCount());
            stepDetails.put("commitCount", stepExecution.getCommitCount());
            stepDetails.put("rollbackCount", stepExecution.getRollbackCount());
            stepDetails.put("readSkipCount", stepExecution.getReadSkipCount());
            stepDetails.put("processSkipCount", stepExecution.getProcessSkipCount());
            stepDetails.put("writeSkipCount", stepExecution.getWriteSkipCount());
            stepDetails.put("filterCount", stepExecution.getFilterCount());
            stepDetails.put("startTime", stepExecution.getStartTime());
            stepDetails.put("endTime", stepExecution.getEndTime());
            stepDetails.put("exitStatus", stepExecution.getExitStatus().getExitCode());
            return stepDetails;
        }).collect(Collectors.toList()));

        return execDetails;
    }
}
