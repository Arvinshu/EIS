/**
 * 目录结构: ElasticsearchIndexService/src/main/java/org/ls/indexer/batch/JobCompletionNotificationListener.java
 * 文件名称: JobCompletionNotificationListener.java
 * 开发时间: 2025-05-19 04:30:00 UTC/GMT+08:00 (上次编辑时间)
 * 作者: Gemini
 * 代码用途: Spring Batch 作业执行监听器，在作业开始前和完成后记录日志。
 */
package org.ls.indexer.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

import java.time.Duration; // 新增导入
import java.time.LocalDateTime; // 新增导入

public class JobCompletionNotificationListener implements JobExecutionListener {

    private static final Logger logger = LoggerFactory.getLogger(JobCompletionNotificationListener.class);

    @Override
    public void beforeJob(JobExecution jobExecution) {
        logger.info("批处理作业 '{}' 即将开始... Job ID: {}, Parameters: {}",
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getJobId(),
                jobExecution.getJobParameters());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        BatchStatus status = jobExecution.getStatus();
        long jobId = jobExecution.getJobId();

        if (status == BatchStatus.COMPLETED) {
            logger.info("批处理作业 '{}' (ID: {}) 已成功完成！", jobName, jobId);
        } else if (status == BatchStatus.FAILED) {
            logger.error("批处理作业 '{}' (ID: {}) 执行失败！", jobName, jobId);
            jobExecution.getAllFailureExceptions().forEach(ex ->
                    logger.error("  失败原因: {}", ex.getMessage(), ex)
            );
        } else if (status == BatchStatus.STOPPED) {
            logger.warn("批处理作业 '{}' (ID: {}) 已被停止。", jobName, jobId);
        } else {
            logger.info("批处理作业 '{}' (ID: {}) 完成，状态: {}", jobName, jobId, status);
        }

        LocalDateTime startTime = jobExecution.getStartTime();
        LocalDateTime endTime = jobExecution.getEndTime();
        String durationString = "N/A";

        if (startTime != null && endTime != null) {
            Duration duration = Duration.between(startTime, endTime);
            durationString = String.format("%d 分 %d 秒 %d 毫秒",
                    duration.toMinutesPart(),
                    duration.toSecondsPart(),
                    duration.toMillisPart());
            // 或者直接显示总毫秒数: duration.toMillis() + " ms"
        }


        logger.info("作业 '{}' (ID: {}) 开始时间: {}, 结束时间: {}, 持续时间: {}, 退出状态: {}",
                jobName,
                jobId,
                startTime,
                endTime,
                durationString,
                jobExecution.getExitStatus().getExitCode()
        );
    }
}
