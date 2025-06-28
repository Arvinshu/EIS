/**
 * 目录结构: ElasticsearchIndexService/src/main/resources/static/indexer-monitor/js/apiService.js
 * 文件名称: apiService.js
 * 开发时间: 2025-05-19 05:30:00 UTC/GMT+08:00
 * 作者: Gemini
 * 代码用途: 封装对后端API的调用。
 */

const API_BASE_URL = '/api'; // 根据实际后端API基础路径调整

/**
 * 通用的 API 请求函数。
 * @param {string} endpoint - API 端点路径 (例如, /status/health)。
 * @param {object} [options={}] - fetch API 的选项 (例如, method, headers, body)。
 * @returns {Promise<any>} - 解析后的 JSON 数据或在错误时 reject Promise。
 */
async function fetchData(endpoint, options = {}) {
    const url = `${API_BASE_URL}${endpoint}`;
    try {
        const response = await fetch(url, options);
        if (!response.ok) {
            // 如果 HTTP 状态码不在 200-299 范围，则尝试解析错误体
            const errorData = await response.json().catch(() => ({ message: response.statusText }));
            console.error(`API Error ${response.status}: ${errorData.message || response.statusText} for URL: ${url}`);
            throw new Error(`HTTP error ${response.status}: ${errorData.message || response.statusText}`);
        }
        // 如果响应状态码是 204 No Content，则 response.json() 会报错
        if (response.status === 204) {
            return null; // 或者返回一个表示成功的特定对象
        }
        return await response.json();
    } catch (error) {
        console.error(`Fetch error for ${url}:`, error);
        throw error; // 将错误继续抛出，以便调用方处理
    }
}

// --- 服务状态 API ---
const apiService = {
    /**
     * 获取应用健康状况。
     * GET /api/status/health
     */
    getApplicationHealth: () => {
        return fetchData('/status/health');
    },

    /**
     * 获取 Elasticsearch 集群健康状况。
     * GET /api/status/elasticsearch/cluster-health
     */
    getEsClusterHealth: () => {
        return fetchData('/status/elasticsearch/cluster-health');
    },

    /**
     * 获取指定 Elasticsearch 索引的统计信息。
     * GET /api/status/elasticsearch/index-stats/{indexName}
     * @param {string} indexName - 索引名称。
     */
    getEsIndexStats: (indexName) => {
        if (!indexName) {
            console.warn('getEsIndexStats: indexName is required.');
            return Promise.resolve(null); // 或者 reject
        }
        return fetchData(`/status/elasticsearch/index-stats/${indexName}`);
    },

    /**
     * 获取 Kafka DLQ 摘要信息。
     * GET /api/status/kafka/dlq-summary
     */
    getKafkaDlqSummary: () => {
        return fetchData('/status/kafka/dlq-summary');
    },

    /**
     * 获取 Kafka 消费者 Lag 信息。
     * GET /api/status/kafka/consumer-groups/lag
     */
    getKafkaConsumerLag: () => {
        return fetchData('/status/kafka/consumer-groups/lag');
    },

    // --- Batch 作业 API ---
    /**
     * 启动历史数据批量索引作业。
     * POST /api/batch/historical-index/start
     */
    startBatchJob: () => {
        return fetchData('/batch/historical-index/start', { method: 'POST' });
    },

    /**
     * 查询特定作业执行的状态。
     * GET /api/batch/historical-index/status/{jobExecutionId}
     * @param {number|string} jobExecutionId - 作业执行ID。
     */
    getBatchJobStatus: (jobExecutionId) => {
        if (!jobExecutionId) {
            console.warn('getBatchJobStatus: jobExecutionId is required.');
            return Promise.resolve(null);
        }
        return fetchData(`/batch/historical-index/status/${jobExecutionId}`);
    },

    /**
     * 查询最新的历史数据索引作业的执行状态列表。
     * GET /api/batch/historical-index/latest-status
     */
    getLatestBatchJobStatuses: () => {
        return fetchData('/batch/historical-index/latest-status');
    },

    // --- DLQ 消息管理 API (占位符，后续实现) ---
    /**
     * 查看指定DLQ Topic的消息 (占位符)。
     * @param {string} dlqTopicName - DLQ Topic 名称。
     */
    viewDlqMessages: (dlqTopicName) => {
        console.warn(`viewDlqMessages for ${dlqTopicName} is not yet implemented.`);
        // return fetchData(`/dlq/${dlqTopicName}/messages`); // 示例端点
        return Promise.resolve({ messages: [], message: "功能待实现" });
    },

    /**
     * 重试指定DLQ Topic中的所有消息 (占位符)。
     * @param {string} dlqTopicName - DLQ Topic 名称。
     */
    retryAllDlqMessages: (dlqTopicName) => {
        console.warn(`retryAllDlqMessages for ${dlqTopicName} is not yet implemented.`);
        // return fetchData(`/dlq/${dlqTopicName}/retry-all`, { method: 'POST' }); // 示例端点
        return Promise.resolve({ success: false, message: "功能待实现" });
    },

    /**
     * 删除指定DLQ Topic中的所有消息 (占位符)。
     * @param {string} dlqTopicName - DLQ Topic 名称。
     */
    deleteAllDlqMessages: (dlqTopicName) => {
        console.warn(`deleteAllDlqMessages for ${dlqTopicName} is not yet implemented.`);
        // return fetchData(`/dlq/${dlqTopicName}/delete-all`, { method: 'POST' }); // 示例端点
        return Promise.resolve({ success: false, message: "功能待实现" });
    }
};

// 如果在模块化环境 (例如使用 ES6 模块)，则使用 export
// export default apiService;
