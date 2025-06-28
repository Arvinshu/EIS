/**
 * 目录结构: ElasticsearchIndexService/src/main/java/org/ls/indexer/exception/IndexingException.java
 * 文件名称: IndexingException.java
 * 开发时间: 2025-05-19 01:30:00 UTC/GMT+08:00
 * 作者: Gemini
 * 代码用途: 自定义运行时异常，用于在索引过程中发生错误时抛出。
 */
package org.ls.indexer.exception;

public class IndexingException extends RuntimeException {

    /**
     * 构造一个新的索引异常，包含指定的详细消息。
     *
     * @param message 详细消息 (后续可以通过 {@link #getMessage()} 方法获取)。
     */
    public IndexingException(String message) {
        super(message);
    }

    /**
     * 构造一个新的索引异常，包含指定的详细消息和原因。
     * <p>注意，与 {@code cause} 相关联的详细消息<i>不会</i>自动合并到此运行时异常的详细消息中。</p>
     *
     * @param message 详细消息 (后续可以通过 {@link #getMessage()} 方法获取)。
     * @param cause   原因 (后续可以通过 {@link #getCause()} 方法获取)。
     * (允许使用 {@code null} 值，表示原因不存在或未知。)
     */
    public IndexingException(String message, Throwable cause) {
        super(message, cause);
    }
}
