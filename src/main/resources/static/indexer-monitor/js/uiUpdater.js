/**
 * 目录结构: ElasticsearchIndexService/src/main/resources/static/indexer-monitor/js/uiUpdater.js
 * 文件名称: uiUpdater.js
 * 开发时间: 2025-05-19 05:30:05 UTC/GMT+08:00
 * 作者: Gemini
 * 代码用途: 包含所有更新DOM元素的函数，供其他JS模块调用。
 */

const uiUpdater = {
    /**
     * 安全地更新元素的文本内容。
     * @param {string} elementId - 目标元素的 ID。
     * @param {string|number} text - 要设置的文本内容。
     * @param {string} [defaultValue='-'] - 如果 text 为 null 或 undefined，则使用的默认值。
     */
    updateText: (elementId, text, defaultValue = '-') => {
        const element = document.getElementById(elementId);
        if (element) {
            element.textContent = (text !== null && typeof text !== 'undefined') ? String(text) : defaultValue;
        } else {
            console.warn(`UI Updater: Element with ID '${elementId}' not found.`);
        }
    },

    /**
     * 更新状态指示灯的类。
     * @param {string} elementId - 指示灯元素的 ID。
     * @param {string} status - 状态字符串 ('UP', 'DOWN', 'WARN', 'UNKNOWN', 'GREEN', 'YELLOW', 'RED' 等)。
     */
    updateStatusIndicator: (elementId, status) => {
        const element = document.getElementById(elementId);
        if (element) {
            element.className = 'status-indicator'; // Reset classes
            switch (String(status).toUpperCase()) {
                case 'UP':
                case 'GREEN':
                case 'CONNECTED': // 自定义状态
                    element.classList.add('status-up');
                    break;
                case 'DOWN':
                case 'RED':
                case 'DISCONNECTED': // 自定义状态
                    element.classList.add('status-down');
                    break;
                case 'WARN':
                case 'YELLOW':
                case 'DEGRADED': // 自定义状态
                    element.classList.add('status-warn');
                    break;
                default:
                    element.classList.add('status-unknown');
                    break;
            }
        } else {
            console.warn(`UI Updater: Status indicator with ID '${elementId}' not found.`);
        }
    },

    /**
     * 切换元素的显示/隐藏状态 (通过 'hidden' Tailwind 类)。
     * @param {string} elementId - 目标元素的 ID。
     * @param {boolean} show - true 表示显示, false 表示隐藏。
     */
    toggleElementVisibility: (elementId, show) => {
        const element = document.getElementById(elementId);
        if (element) {
            if (show) {
                element.classList.remove('hidden');
            } else {
                element.classList.add('hidden');
            }
        } else {
            console.warn(`UI Updater: Element with ID '${elementId}' for visibility toggle not found.`);
        }
    },

    /**
     * 更新服务概览信息。
     * @param {object} healthData - 从 /api/status/health 获取的数据。
     * @param {object} esHealthData - 从 /api/status/elasticsearch/cluster-health 获取的数据。
     * @param {object} kafkaLagData - 从 /api/status/kafka/consumer-groups/lag 获取的数据 (用于判断连接状态)。
     */
    updateServiceOverview: (healthData, esHealthData, kafkaLagData) => {
        // Indexer Service Status (基于Spring Actuator health)
        if (healthData && healthData.status) {
            uiUpdater.updateText('indexerStatus', healthData.status);
            uiUpdater.updateStatusIndicator('indexerStatusIndicator', healthData.status);
        } else {
            uiUpdater.updateText('indexerStatus', '获取失败');
            uiUpdater.updateStatusIndicator('indexerStatusIndicator', 'UNKNOWN');
        }

        // Elasticsearch Connection Status (基于ES集群健康API的响应)
        if (esHealthData && esHealthData.status) { // ES cluster health status (GREEN, YELLOW, RED)
            uiUpdater.updateText('esConnectionStatus', esHealthData.status);
            uiUpdater.updateStatusIndicator('esConnectionIndicator', esHealthData.status);
        } else if (esHealthData && esHealthData.error) { // API调用本身失败
            uiUpdater.updateText('esConnectionStatus', '连接失败');
            uiUpdater.updateStatusIndicator('esConnectionIndicator', 'DOWN');
        }
        else {
            uiUpdater.updateText('esConnectionStatus', '未知');
            uiUpdater.updateStatusIndicator('esConnectionIndicator', 'UNKNOWN');
        }

        // Kafka Connection Status (可以基于能否成功获取Lag信息来判断)
        // 这是一个间接的判断，更准确的判断可能需要专门的Kafka连接健康检查端点
        if (kafkaLagData && !kafkaLagData.error) { // 假设成功获取lag信息意味着连接正常
            uiUpdater.updateText('kafkaConnectionStatus', '已连接');
            uiUpdater.updateStatusIndicator('kafkaConnectionIndicator', 'UP');
        } else if (kafkaLagData && kafkaLagData.error) {
            uiUpdater.updateText('kafkaConnectionStatus', '连接失败');
            uiUpdater.updateStatusIndicator('kafkaConnectionIndicator', 'DOWN');
        }
        else {
            uiUpdater.updateText('kafkaConnectionStatus', '未知');
            uiUpdater.updateStatusIndicator('kafkaConnectionIndicator', 'UNKNOWN');
        }

        uiUpdater.updateText('lastDataUpdate', new Date().toLocaleTimeString());
    },

    /**
     * 更新 Kafka 消费者统计信息。
     * @param {object} lagData - 从 /api/status/kafka/consumer-groups/lag 获取的数据。
     * @param {object} topicNames - 包含 upsertTopicName 和 deleteTopicName 的对象。
     */
    updateKafkaConsumerStats: (lagData, topicNames) => {
        // 假设 lagData.group_your-consumer-group-id.listenerId 包含特定监听器的信息
        // 我们需要根据 topicNames.upsertTopicName 和 topicNames.deleteTopicName 来找到对应的分区lag

        const upsertTopic = topicNames.upsertTopicName;
        const deleteTopic = topicNames.deleteTopicName;

        uiUpdater.updateText('kafkaUpsertTopicName', upsertTopic);
        uiUpdater.updateText('kafkaDeleteTopicName', deleteTopic);

        let upsertGroupId = '-';
        let upsertAssignedPartitions = '-';
        let upsertTotalLag = '-';
        let upsertLagPerPartition = '-';

        let deleteGroupId = '-';
        let deleteAssignedPartitions = '-';
        let deleteTotalLag = '-';
        let deleteLagPerPartition = '-';

        if (lagData && typeof lagData === 'object' && Object.keys(lagData).length > 0 && !lagData.error) {
            for (const groupKey in lagData) {
                const groupInfo = lagData[groupKey];
                if (groupInfo && groupInfo.partitions && Array.isArray(groupInfo.partitions)) {
                    let currentGroupTotalLag = 0;
                    const partitionLags = [];
                    const assignedPartitionsForGroup = [];

                    let isUpsertGroup = false;
                    let isDeleteGroup = false;

                    groupInfo.partitions.forEach(p => {
                        if (p.topic === upsertTopic) isUpsertGroup = true;
                        if (p.topic === deleteTopic) isDeleteGroup = true;

                        assignedPartitionsForGroup.push(p.partition);
                        partitionLags.push(`P${p.partition}: ${p.lag}`);
                        if (p.lag > 0) currentGroupTotalLag += p.lag;
                    });

                    const groupIdDisplay = groupKey.replace('group_', ''); // 从 "group_dms-es-indexer-group" 提取

                    if (isUpsertGroup) {
                        upsertGroupId = groupIdDisplay;
                        upsertAssignedPartitions = assignedPartitionsForGroup.join(', ') || '无';
                        upsertTotalLag = currentGroupTotalLag;
                        upsertLagPerPartition = partitionLags.join(', ') || '无';
                    }
                    if (isDeleteGroup) { // 注意：如果同一个group消费多个topic，这里可能会覆盖
                        deleteGroupId = groupIdDisplay;
                        deleteAssignedPartitions = assignedPartitionsForGroup.join(', ') || '无';
                        deleteTotalLag = currentGroupTotalLag;
                        deleteLagPerPartition = partitionLags.join(', ') || '无';
                    }
                    // 如果一个消费者组同时消费这两个topic，上面的逻辑需要调整以分别显示
                    // 简化处理：假设一个组主要对应一个业务逻辑的topic集
                }
            }
        }

        uiUpdater.updateText('kafkaUpsertGroupId', upsertGroupId);
        uiUpdater.updateText('kafkaUpsertAssignedPartitions', upsertAssignedPartitions);
        uiUpdater.updateText('kafkaUpsertTotalLag', upsertTotalLag);
        uiUpdater.updateText('kafkaUpsertLagPerPartition', upsertLagPerPartition);

        uiUpdater.updateText('kafkaDeleteGroupId', deleteGroupId);
        uiUpdater.updateText('kafkaDeleteAssignedPartitions', deleteAssignedPartitions);
        uiUpdater.updateText('kafkaDeleteTotalLag', deleteTotalLag);
        uiUpdater.updateText('kafkaDeleteLagPerPartition', deleteLagPerPartition);
    },

    /**
     * 更新 Elasticsearch 索引统计信息。
     * @param {object} indexStatsData - 从 /api/status/elasticsearch/index-stats/{indexName} 获取的数据。
     * @param {string} indexName - 索引名称。
     */
    updateElasticsearchIndexStats: (indexStatsData, indexName) => {
        uiUpdater.updateText('esIndexName', indexName);
        if (indexStatsData && !indexStatsData.error) {
            uiUpdater.updateText('esIndexStatus', indexStatsData.health || '未知');
            uiUpdater.updateStatusIndicator('esIndexStatusIndicator', indexStatsData.health || 'UNKNOWN');
            uiUpdater.updateText('esTotalDocs', indexStatsData.documentCount);
            uiUpdater.updateText('esIndexSizePrimary', indexStatsData.primaryStoreSize || indexStatsData.storeSize); // 优先主分片，否则总大小
            uiUpdater.updateText('esIndexSizeTotal', indexStatsData.storeSize);

            // uiUpdater.updateText('esLastSuccessfulIndexOp', '待实现'); // 需要后端提供此信息
            // uiUpdater.updateText('esLastIndexingError', '待实现'); // 需要后端提供此信息
        } else {
            const errorMsg = (indexStatsData && indexStatsData.message) ? indexStatsData.message : '获取失败';
            uiUpdater.updateText('esIndexStatus', errorMsg);
            uiUpdater.updateStatusIndicator('esIndexStatusIndicator', 'UNKNOWN');
            uiUpdater.updateText('esTotalDocs', '-');
            uiUpdater.updateText('esIndexSizePrimary', '-');
            uiUpdater.updateText('esIndexSizeTotal', '-');
        }
    },

    /**
     * 更新 DLQ 摘要信息。
     * @param {object} dlqSummaryData - 从 /api/status/kafka/dlq-summary 获取的数据。
     * @param {object} topicNames - 包含 DLQ Topic 名称的对象。
     */
    updateDlqSummary: (dlqSummaryData, topicNames) => {
        uiUpdater.updateText('dlqUpsertTopicName', topicNames.upsertDlqTopicName);
        uiUpdater.updateText('dlqDeleteTopicName', topicNames.deleteDlqTopicName);

        if (dlqSummaryData && !dlqSummaryData.error) {
            uiUpdater.updateText('dlqUpsertCount', dlqSummaryData[topicNames.upsertDlqTopicName + '_message_count_approx'] || 0);
            uiUpdater.updateText('dlqDeleteCount', dlqSummaryData[topicNames.deleteDlqTopicName + '_message_count_approx'] || 0);
        } else {
            uiUpdater.updateText('dlqUpsertCount', '获取失败');
            uiUpdater.updateText('dlqDeleteCount', '获取失败');
        }
    },

    /**
     * 更新历史数据批量索引作业的状态信息。
     * @param {object} jobExecutionData - 单个作业执行的数据，或null。
     */
    updateBatchJobStatusDisplay: (jobExecutionData) => {
        if (jobExecutionData && !jobExecutionData.error) {
            uiUpdater.updateText('batchJobStatus', jobExecutionData.status);
            uiUpdater.updateText('batchLastRunId', jobExecutionData.jobExecutionId);
            uiUpdater.updateText('batchLastRunStartTime', jobExecutionData.startTime ? new Date(jobExecutionData.startTime).toLocaleString() : '-');
            uiUpdater.updateText('batchLastRunEndTime', jobExecutionData.endTime ? new Date(jobExecutionData.endTime).toLocaleString() : '-');

            let duration = '-';
            if (jobExecutionData.startTime && jobExecutionData.endTime) {
                const diff = new Date(jobExecutionData.endTime).getTime() - new Date(jobExecutionData.startTime).getTime();
                duration = `${(diff / 1000).toFixed(2)} 秒`;
            }
            uiUpdater.updateText('batchLastRunDuration', duration);

            // 从步骤执行中汇总统计数据
            let filesScanned = 0; // readCount
            let filesProcessed = 0; // writeCount (因为processor的输出是writer的输入)
            let successfullyIndexed = 0; // writeCount
            let failedToIndex = 0; // processSkipCount + writeSkipCount

            if (jobExecutionData.stepExecutions && jobExecutionData.stepExecutions.length > 0) {
                const stepExec = jobExecutionData.stepExecutions[0]; // 假设只有一个步骤
                filesScanned = stepExec.readCount || 0;
                filesProcessed = (stepExec.writeCount || 0) + (stepExec.writeSkipCount || 0); // 写入的 + 写入跳过的
                successfullyIndexed = stepExec.writeCount || 0;
                failedToIndex = (stepExec.processSkipCount || 0) + (stepExec.writeSkipCount || 0);
            }

            uiUpdater.updateText('batchFilesScanned', filesScanned);
            uiUpdater.updateText('batchFilesProcessed', filesProcessed); // Processed = written + skipped by writer
            uiUpdater.updateText('batchSuccessfullyIndexed', successfullyIndexed);
            uiUpdater.updateText('batchFailedToIndex', failedToIndex);

            // 更新进度条
            const progressBar = document.getElementById('batchJobProgress');
            if (progressBar) {
                let progress = 0;
                if (jobExecutionData.status === 'COMPLETED' || jobExecutionData.status === 'FAILED' || jobExecutionData.status === 'STOPPED') {
                    progress = 100;
                } else if (jobExecutionData.status === 'STARTED' || jobExecutionData.status === 'STARTING') {
                    // 简化的进度：如果已扫描文件，则基于已处理的估算。
                    // 更准确的进度需要后端提供总文件数。
                    if (filesScanned > 0 && filesProcessed <= filesScanned) {
                        progress = Math.min(100, Math.round((filesProcessed / filesScanned) * 100));
                    } else if (filesScanned === 0 && filesProcessed > 0) { // 可能在某些情况下，扫描数未及时更新
                        progress = 5; // 表示已开始
                    } else {
                        progress = 0;
                    }
                }
                progressBar.style.width = `${progress}%`;
                progressBar.textContent = `${progress}%`;
            }
            // 控制按钮状态
            const startButton = document.getElementById('startBatchJobButton');
            if (startButton) {
                startButton.disabled = (jobExecutionData.status === 'STARTING' || jobExecutionData.status === 'STARTED');
            }

        } else {
            uiUpdater.updateText('batchJobStatus', (jobExecutionData && jobExecutionData.error) ? '获取失败' : 'IDLE');
            // 重置其他字段
            ['batchLastRunId', 'batchLastRunStartTime', 'batchLastRunEndTime', 'batchLastRunDuration',
                'batchFilesScanned', 'batchFilesProcessed', 'batchSuccessfullyIndexed', 'batchFailedToIndex'].forEach(id => {
                uiUpdater.updateText(id, '-');
            });
            const progressBar = document.getElementById('batchJobProgress');
            if (progressBar) {
                progressBar.style.width = '0%';
                progressBar.textContent = '0%';
            }
            const startButton = document.getElementById('startBatchJobButton');
            if (startButton) startButton.disabled = false;
        }
    },

    /**
     * 更新历史数据批量索引作业的执行历史列表。
     * @param {Array<object>} jobExecutions - 作业执行历史数据列表。
     */
    updateBatchJobHistory: (jobExecutions) => {
        const container = document.getElementById('batchJobHistoryContainer');
        const messageElement = document.getElementById('batchJobHistoryMessage');
        if (!container || !messageElement) return;

        if (!jobExecutions || jobExecutions.length === 0) {
            messageElement.textContent = '没有可用的作业执行历史。';
            messageElement.classList.remove('hidden');
            // 清空可能存在的旧列表
            while (container.firstChild && container.firstChild !== messageElement) {
                container.removeChild(container.firstChild);
            }
            return;
        }

        messageElement.classList.add('hidden');
        // 清空旧列表
        while (container.firstChild && container.firstChild !== messageElement) {
            container.removeChild(container.firstChild);
        }

        const ul = document.createElement('ul');
        ul.className = 'space-y-1';

        jobExecutions.forEach(exec => {
            const li = document.createElement('li');
            li.className = 'p-1 border-b border-gray-200 hover:bg-gray-100 cursor-pointer';
            li.textContent = `ID: ${exec.jobExecutionId}, Status: ${exec.status}, Start: ${exec.startTime ? new Date(exec.startTime).toLocaleString() : 'N/A'}, End: ${exec.endTime ? new Date(exec.endTime).toLocaleString() : 'N/A'}`;
            li.title = `点击查看详情 (JobExecutionId: ${exec.jobExecutionId})`; // 提示
            li.setAttribute('data-execution-id', exec.jobExecutionId); // 存储ID供点击事件使用
            ul.appendChild(li);
        });
        container.appendChild(ul);
    },


    // --- 辅助函数 ---
    /**
     * 显示一个临时的通知消息 (例如，用于操作结果)。
     * @param {string} message - 要显示的消息。
     * @param {'success'|'error'|'info'} type - 消息类型。
     */
    showNotification: (message, type = 'info') => {
        // 简单实现: alert。在实际应用中，可以使用更美观的通知组件。
        alert(`[${type.toUpperCase()}] ${message}`);
        // TODO: 实现一个更友好的通知栏或toast组件
    },

    /**
     * 更新服务器时间显示。
     * @param {Date} dateObject - Date 对象。
     */
    updateServerTime: (dateObject) => {
        // 这个函数是可选的，如果后端不方便提供服务器时间，前端可以显示本地时间作为“最后更新时间”的参考
        // uiUpdater.updateText('serverTime', dateObject.toLocaleTimeString());
    }

};

// 如果在模块化环境，则使用 export
// export default uiUpdater;
