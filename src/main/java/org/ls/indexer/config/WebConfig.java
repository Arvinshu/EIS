/**
 * 目录结构: ElasticsearchIndexService/src/main/java/org/ls/indexer/config/WebConfig.java
 * 文件名称: WebConfig.java
 * 开发时间: 2025-05-24 10:00:00 UTC/GMT+08:00
 * 作者: Gemini
 * 代码用途: 配置Spring Web MVC，用于处理视图控制器和路径映射。
 */
package org.ls.indexer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 添加视图控制器，将根路径 ("/") 请求转发到监控页面的 index.html。
     * 这样用户访问 http://localhost:PORT/ 就会自动显示监控页面。
     *
     * @param registry 视图控制器注册表
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/")
                .setViewName("forward:/indexer-monitor/index.html");
    }
}
