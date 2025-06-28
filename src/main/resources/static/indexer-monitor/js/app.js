/**
 * 目录结构: ElasticsearchIndexService/src/main/resources/static/indexer-monitor/js/app.js
 * 文件名称: app.js
 * 开发时间: 2025-05-19 05:45:00 UTC/GMT+08:00
 * 作者: Gemini
 * 代码用途: 监控页面的主 JavaScript 文件，负责初始化、事件处理和数据轮询。
 */

// --- 配置常量 ---
const KAFKA_UPSERT_TOPIC_NAME = 'dms-file-upsert-events'; // 应与后端配置一致
const KAFKA_DELETE_TOPIC_NAME = 'dms-file-delete-events'; // 应与后端配置一致
const KAFKA_UPSERT_DLQ_TOPIC_NAME = 'dms-file-upsert-events-dlq';
const KAFKA_DELETE_DLQ_TOPIC_NAME = 'dms-file-delete-events-dlq';
const ES_INDEX_NAME = 'dms_files'; // 应与后端配置一致

const TOPIC_NAMES = {
    upsertTopicName: KAFKA_UPSERT_TOPIC_NAME,
    deleteTopicName: KAFKA_DELETE_TOPIC_NAME,
    upsertDlqTopicName: KAFKA_UPSERT_DLQ_TOPIC_NAME,
    deleteDlqTopicName: KAFKA_DELETE_DLQ_TOPIC_NAME,
};

// --- 全局状态 ---
let autoRefreshIntervalId = null;
let currentRefreshInterval = 30000; // 默认30秒
let isAutoRefreshEnabled = false;
let currentJobExecutionIdForPolling = null; // 用于轮询特定批处理作业的状态

// --- DOMContentLoaded ---
document.addEventListener('DOMContentLoaded', () => {
    console.log('DOM fully loaded and parsed. Initializing app...');
    initializePage();
    setupEventListeners();
    fetchAllData(); // 首次加载数据
});

/**
 * 初始化页面元素和状态。
 */
function initializePage() {
    // 设置默认的Topic名称到UI (如果HTML中没有硬编码)
    uiUpdater.updateText('kafkaUpsertTopicName', TOPIC_NAMES.upsertTopicName);
    uiUpdater.updateText('kafkaDeleteTopicName', TOPIC_NAMES.deleteTopicName);
    uiUpdater.updateText('dlqUpsertTopicName', TOPIC_NAMES.upsertDlqTopicName);
    uiUpdater.updateText('dlqDeleteTopicName', TOPIC_NAMES.deleteDlqTopicName);
    uiUpdater.updateText('esIndexName', ES_INDEX_NAME);

    // 初始化自动刷新选择框和复选框的状态
    const autoRefreshToggle = document.getElementById('autoRefreshToggle');
    const autoRefreshIntervalSelect = document.getElementById('autoRefreshInterval');
    if (autoRefreshToggle) autoRefreshToggle.checked = isAutoRefreshEnabled;
    if (autoRefreshIntervalSelect) {
        autoRefreshIntervalSelect.value = currentRefreshInterval;
        autoRefreshIntervalSelect.disabled = !isAutoRefreshEnabled;
    }
    // 初始化按钮状态
    const startBatchJobButton = document.getElementById('startBatchJobButton');
    if (startBatchJobButton) startBatchJobButton.disabled = false;
    const stopBatchJobButton = document.getElementById('stopBatchJobButton');
    if (stopBatchJobButton) stopBatchJobButton.disabled = true; // 停止功能待实现
}

/**
 * 设置页面元素的事件监听器。
 */
function setupEventListeners() {
    const refreshAllButton = document.getElementById('refreshAllButton');
    if (refreshAllButton) {
        refreshAllButton.addEventListener('click', fetchAllData);
    }

    const autoRefreshToggle = document.getElementById('autoRefreshToggle');
    const autoRefreshIntervalSelect = document.getElementById('autoRefreshInterval');

    if (autoRefreshToggle) {
        autoRefreshToggle.addEventListener('change', (event) => {
            isAutoRefreshEnabled = event.target.checked;
            if (autoRefreshIntervalSelect) autoRefreshIntervalSelect.disabled = !isAutoRefreshEnabled;
            if (isAutoRefreshEnabled) {
                startAutoRefresh();
            } else {
                stopAutoRefresh();
            }
        });
    }

    if (autoRefreshIntervalSelect) {
        autoRefreshIntervalSelect.addEventListener('change', (event) => {
            currentRefreshInterval = parseInt(event.target.value, 10);
            if (isAutoRefreshEnabled) {
                stopAutoRefresh(); // 清除旧的定时器
                startAutoRefresh(); // 用新的间隔启动
            }
        });
    }

    // Batch Job 相关按钮
    const startBatchJobButton = document.getElementById('startBatchJobButton');
    if (startBatchJobButton) {
        startBatchJobButton.addEventListener('click', handleStartBatchJob);
    }

    // DLQ 相关按钮 (查看、重试、删除 - 当前为占位符)
    document.getElementById('viewUpsertDlqMessages')?.addEventListener('click', () => uiUpdater.showNotification('查看 Upsert DLQ 消息功能待实现。'));
    document.getElementById('retryUpsertDlqAll')?.addEventListener('click', () => uiUpdater.showNotification('重试 Upsert DLQ 功能待实现。'));
    document.getElementById('deleteUpsertDlqAll')?.addEventListener('click', () => uiUpdater.showNotification('删除 Upsert DLQ 功能待实现。'));

    document.getElementById('viewDeleteDlqMessages')?.addEventListener('click', () => uiUpdater.showNotification('查看 Delete DLQ 消息功能待实现。'));
    document.getElementById('retryDeleteDlqAll')?.addEventListener('click', () => uiUpdater.showNotification('重试 Delete DLQ 功能待实现。'));
    document.getElementById('deleteDeleteDlqAll')?.addEventListener('click', () => uiUpdater.showNotification('删除 Delete DLQ 功能待实现。'));

    // 作业历史列表点击事件 (事件委托)
    const jobHistoryContainer = document.getElementById('batchJobHistoryContainer');
    if (jobHistoryContainer) {
        jobHistoryContainer.addEventListener('click', (event) => {
            const listItem = event.target.closest('li[data-execution-id]');
            if (listItem) {
                const executionId = listItem.getAttribute('data-execution-id');
                console.log(`Clicked job history item with execution ID: ${executionId}`);
                fetchAndDisplaySpecificJobStatus(executionId);
            }
        });
    }
}

/**
 * 启动自动刷新。
 */
function startAutoRefresh() {
    if (autoRefreshIntervalId) {
        clearInterval(autoRefreshIntervalId);
    }
    if (isAutoRefreshEnabled && currentRefreshInterval > 0) {
        autoRefreshIntervalId = setInterval(fetchAllData, currentRefreshInterval);
        console.log(`自动刷新已启动，间隔: ${currentRefreshInterval / 1000}秒`);
    }
}

/**
 * 停止自动刷新。
 */
function stopAutoRefresh() {
    if (autoRefreshIntervalId) {
        clearInterval(autoRefreshIntervalId);
        autoRefreshIntervalId = null;
        console.log('自动刷新已停止。');
    }
}

/**
 * 获取并显示所有监控数据。
 */
async function fetchAllData() {
    console.log('正在获取所有监控数据...');
    uiUpdater.updateText('lastDataUpdate', '加载中...');

    try {
        // 并行获取数据
        const [healthData, esHealthData, kafkaLagData, dlqSummaryData, latestBatchJobs] = await Promise.all([
            apiService.getApplicationHealth().catch(handleApiError('应用健康状况')),
            apiService.getEsClusterHealth().catch(handleApiError('ES集群健康')),
            apiService.getKafkaConsumerLag().catch(handleApiError('Kafka消费者Lag')),
            apiService.getKafkaDlqSummary().catch(handleApiError('Kafka DLQ摘要')),
            apiService.getLatestBatchJobStatuses().catch(handleApiError('最新批处理作业'))
        ]);

        // 更新服务概览 (需要 healthData, esHealthData, kafkaLagData)
        uiUpdater.updateServiceOverview(healthData, esHealthData, kafkaLagData);

        // 更新Kafka消费者统计
        if (kafkaLagData) {
            uiUpdater.updateKafkaConsumerStats(kafkaLagData, TOPIC_NAMES);
        }

        // 更新ES索引统计 (需要先获取索引名称，这里用常量)
        const esIndexStatsData = await apiService.getEsIndexStats(ES_INDEX_NAME).catch(handleApiError(`ES索引 '${ES_INDEX_NAME}' 统计`));
        if (esIndexStatsData) {
            uiUpdater.updateElasticsearchIndexStats(esIndexStatsData, ES_INDEX_NAME);
        }

        // 更新DLQ摘要
        if (dlqSummaryData) {
            uiUpdater.updateDlqSummary(dlqSummaryData, TOPIC_NAMES);
        }

        // 更新批处理作业历史
        if (latestBatchJobs) {
            uiUpdater.updateBatchJobHistory(latestBatchJobs);
            // 如果没有当前轮询的作业ID，或者当前轮询的作业已完成，则显示最新的一个（如果存在）
            const shouldUpdateBatchDisplay = !currentJobExecutionIdForPolling ||
                (currentJobExecutionIdForPolling && latestBatchJobs.find(job => job.jobExecutionId === currentJobExecutionIdForPolling && ['COMPLETED', 'FAILED', 'STOPPED'].includes(job.status)));

            if (shouldUpdateBatchDisplay && latestBatchJobs.length > 0) {
                // 默认显示最新的作业状态，或者如果当前轮询的作业已结束，也显示最新的
                const latestJobToDisplay = latestBatchJobs[0]; // 假设列表已按时间倒序
                uiUpdater.updateBatchJobStatusDisplay(latestJobToDisplay);
                if (latestJobToDisplay && !['COMPLETED', 'FAILED', 'STOPPED'].includes(latestJobToDisplay.status)) {
                    currentJobExecutionIdForPolling = latestJobToDisplay.jobExecutionId;
                } else {
                    currentJobExecutionIdForPolling = null; // 清除轮询ID
                }
            } else if (!currentJobExecutionIdForPolling && latestBatchJobs.length === 0) {
                // 如果没有作业历史且没有正在轮询的作业
                uiUpdater.updateBatchJobStatusDisplay(null); // 清空显示
            }
        } else {
            uiUpdater.updateBatchJobHistory([]); // 清空历史
            uiUpdater.updateBatchJobStatusDisplay(null); // 清空当前作业显示
        }

        // 如果有正在运行的批处理作业ID，则单独轮询其状态
        if (currentJobExecutionIdForPolling) {
            fetchAndDisplaySpecificJobStatus(currentJobExecutionIdForPolling);
        }


    } catch (error) {
        console.error('获取全部数据时发生顶层错误:', error);
        uiUpdater.showNotification('获取部分或全部监控数据失败，请检查控制台日志。', 'error');
    } finally {
        // 无论成功与否，都更新“最后更新时间”
        // 如果在try块中已经更新，这里可以省略或用作最终确认
        if (document.getElementById('lastDataUpdate').textContent === '加载中...') {
            uiUpdater.updateText('lastDataUpdate', new Date().toLocaleTimeString() + ' (部分数据可能获取失败)');
        }
    }
}

/**
 * 处理API调用中的错误。
 * @param {string} apiName - 发生错误的API的描述性名称。
 * @returns {function} - 错误处理函数。
 */
function handleApiError(apiName) {
    return (error) => {
        console.error(`获取 '${apiName}' 数据失败:`, error.message || error);
        // 返回一个包含错误信息的对象，以便UI更新函数可以识别并显示错误状态
        return { error: true, message: error.message || '未知错误', source: apiName };
    };
}


/**
 * 处理启动历史数据批量索引作业的请求。
 */
async function handleStartBatchJob() {
    const startButton = document.getElementById('startBatchJobButton');
    if (startButton) startButton.disabled = true; // 禁用按钮防止重复点击
    uiUpdater.updateText('batchJobStatus', '请求启动中...');
    uiUpdater.showNotification('正在请求启动历史数据索引作业...', 'info');

    try {
        const result = await apiService.startBatchJob();
        if (result && result.jobExecutionId) {
            uiUpdater.showNotification(`作业已请求启动。ID: ${result.jobExecutionId}, 状态: ${result.status}`, 'success');
            currentJobExecutionIdForPolling = result.jobExecutionId; // 设置当前轮询的作业ID
            fetchAndDisplaySpecificJobStatus(result.jobExecutionId); // 立即获取一次状态
            fetchAllData(); // 刷新整个面板，特别是作业历史列表
        } else {
            uiUpdater.showNotification('启动作业失败，未收到有效的作业执行ID。', 'error');
            if (startButton) startButton.disabled = false;
        }
    } catch (error) {
        console.error('启动批处理作业失败:', error);
        uiUpdater.showNotification(`启动作业失败: ${error.message || '未知错误'}`, 'error');
        uiUpdater.updateText('batchJobStatus', '启动失败');
        if (startButton) startButton.disabled = false;
    }
}

/**
 * 获取并显示特定批处理作业的执行状态。
 * @param {number|string} jobExecutionId - 作业执行ID。
 */
async function fetchAndDisplaySpecificJobStatus(jobExecutionId) {
    if (!jobExecutionId) return;
    console.log(`正在获取作业执行 ID: ${jobExecutionId} 的状态...`);
    try {
        const jobStatusData = await apiService.getBatchJobStatus(jobExecutionId);
        if (jobStatusData) {
            uiUpdater.updateBatchJobStatusDisplay(jobStatusData);
            // 如果作业仍在运行，则继续轮询此ID；否则清除
            if (jobStatusData.status === 'STARTING' || jobStatusData.status === 'STARTED' || jobStatusData.status === 'STOPPING') {
                currentJobExecutionIdForPolling = jobExecutionId;
            } else {
                // 如果作业完成/失败/停止，并且当前轮询的是这个ID，则清除它，
                // 下次fetchAllData会尝试显示最新的作业（如果存在）
                if (currentJobExecutionIdForPolling === jobExecutionId) {
                    currentJobExecutionIdForPolling = null;
                }
            }
        } else {
            // 如果获取特定作业状态失败，但我们仍在轮询它，则暂时保留轮询ID
            // 可能只是临时网络问题
            console.warn(`未能获取到作业ID ${jobExecutionId} 的状态，将尝试下次轮询。`);
        }
    } catch (error) {
        console.error(`获取作业ID ${jobExecutionId} 状态失败:`, error);
        // 同样，暂时保留轮询ID
    }
}

// --- 模块化占位符 (如果决定拆分逻辑) ---
// const kafkaStatsModule = { init: () => {}, update: (data) => {} };
// const esStatsModule = { init: () => {}, update: (data) => {} };
// const dlqManagerModule = { init: () => {}, update: (data) => {} };
// const batchJobModule = { init: () => {}, update: (data) => {} };
