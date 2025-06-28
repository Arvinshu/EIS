/**
 * 目录结构: ElasticsearchIndexService/src/main/java/org/ls/indexer/dto/FileParseResult.java
 * 文件名称: FileParseResult.java
 * 开发时间: 2025-05-19 01:00:00 UTC/GMT+08:00 (上次编辑时间)
 * 作者: Gemini
 * 代码用途: 数据传输对象 (DTO)，用于封装文件解析服务的结果。
 */
package org.ls.indexer.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor // 此注解将生成包含所有字段的构造函数
public class FileParseResult {

    /**
     * 从文件中提取的纯文本内容。
     */
    private String content;

    /**
     * 从文件元数据中提取的标题。
     * 可能为 null 如果无法提取。
     */
    private String title;

    /**
     * 从文件元数据中提取的作者。
     * 可能为 null 如果无法提取。
     */
    private String author;

    // 移除了与 @AllArgsConstructor 冲突的自定义构造函数

    /**
     * 一个空的解析结果常量，用于表示解析失败或无有效内容提取。
     * 注意: 由于移除了自定义构造函数，此常量需要调整或在使用处直接创建新对象。
     * 或者，可以为此特定目的保留一个静态工厂方法。
     */
    // public static final FileParseResult EMPTY = new FileParseResult(null, null, null);
    // 建议在使用时创建: new FileParseResult() 并依赖字段的默认null值，或 new FileParseResult(null, null, null)
    // 或者定义一个静态工厂方法：
    public static FileParseResult emptyResult() {
        return new FileParseResult(null, null, null);
    }
}
