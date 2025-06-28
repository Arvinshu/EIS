/**
 * 目录结构: ElasticsearchIndexService/src/main/java/org/ls/indexer/service/FileParserService.java
 * 文件名称: FileParserService.java
 * 开发时间: 2025-05-19 01:00:05 UTC/GMT+08:00 (上次编辑时间)
 * 作者: Gemini
 * 代码用途: 使用 Apache Tika 解析文件内容和元数据。
 */
package org.ls.indexer.service;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
// import org.apache.tika.metadata.OfficeOpenXMLCore; // TikaCoreProperties.TITLE 更通用
// import org.apache.tika.metadata.OfficeOpenXMLExtendedProperties; // 移除无法解析的导入
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.ls.indexer.dto.FileParseResult;
import org.ls.indexer.exception.IndexingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class FileParserService {

    private static final Logger logger = LoggerFactory.getLogger(FileParserService.class);

    private Parser tikaParser;
    private Detector tikaDetector;

    @Value("${dms.indexer.tika.write-limit:-1}")
    private int tikaWriteLimit;

    @PostConstruct
    public void init() {
        logger.info("初始化 FileParserService...");
        TikaConfig config = TikaConfig.getDefaultConfig();
        this.tikaParser = new AutoDetectParser(config);
        this.tikaDetector = config.getDetector();
        logger.info("Tika AutoDetectParser 初始化完成。内容提取限制 (writeLimit): {}",
                tikaWriteLimit == -1 ? "无限制" : tikaWriteLimit + "字符");
    }

    public FileParseResult parseFile(Path filePath) throws IndexingException {
        logger.debug("准备解析文件: {}", filePath);
        if (filePath == null || !Files.exists(filePath) || !Files.isReadable(filePath)) {
            logger.warn("文件不存在或不可读: {}", filePath);
            throw new IndexingException("文件不存在或不可读: " + filePath);
        }

        Metadata metadata = new Metadata();
        BodyContentHandler contentHandler = new BodyContentHandler(tikaWriteLimit);
        ParseContext context = new ParseContext();
        context.set(Parser.class, tikaParser);

        try (InputStream stream = Files.newInputStream(filePath)) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filePath.getFileName().toString());
            tikaParser.parse(stream, contentHandler, metadata, context);

            String content = contentHandler.toString().trim();
            String title = extractTitle(metadata);
            String author = extractAuthor(metadata);

            if (logger.isTraceEnabled()) {
                logger.trace("文件 {} 解析完成。标题: '{}', 作者: '{}', 内容长度: {} 字符",
                        filePath, title, author, content.length());
            }
            // 使用静态工厂方法或直接构造
            if (content.isEmpty() && title == null && author == null) {
                // 确保 FileParseResult.java 中有 public static FileParseResult emptyResult() 方法
                return FileParseResult.emptyResult();
            }
            return new FileParseResult(content, title, author);

        } catch (IOException e) {
            logger.error("读取文件 {} 失败: {}", filePath, e.getMessage(), e);
            throw new IndexingException("读取文件失败: " + filePath, e);
        } catch (SAXException | TikaException e) {
            logger.error("Tika 解析文件 {} 失败: {}", filePath, e.getMessage(), e);
            throw new IndexingException("Tika 解析文件失败: " + filePath, e);
        } catch (Exception e) {
            logger.error("解析文件 {} 时发生未知错误: {}", filePath, e.getMessage(), e);
            throw new IndexingException("解析文件时发生未知错误: " + filePath, e);
        }
    }

    private String extractTitle(Metadata metadata) {
        String title = metadata.get(TikaCoreProperties.TITLE);
        if (title == null || title.isEmpty()) {
            // OfficeOpenXMLCore.TITLE 也是一个常见的标题属性，但 TikaCoreProperties.TITLE 通常更通用
            // title = metadata.get(OfficeOpenXMLCore.TITLE);
            // 如果需要，可以添加更多特定格式的标题属性检查
        }
        if (title == null || title.isEmpty()) {
            title = metadata.get("dc:title"); // Dublin Core title
        }
        return title != null ? title.trim() : null;
    }

    private String extractAuthor(Metadata metadata) {
        String author = metadata.get(TikaCoreProperties.CREATOR);
        if (author == null || author.isEmpty()) {
            // 尝试使用字符串 "author" 作为键，如果 Metadata.AUTHOR 常量无法解析
            author = metadata.get("author");
        }
        if (author == null || author.isEmpty()) {
            author = metadata.get(Office.AUTHOR);
        }
        if (author == null || author.isEmpty()) {
            author = metadata.get("dc:creator"); // Dublin Core creator
        }
        // 移除了对 OfficeOpenXMLExtendedProperties.APPLICATION_VERSION 的引用
        return author != null ? author.trim() : null;
    }
}
