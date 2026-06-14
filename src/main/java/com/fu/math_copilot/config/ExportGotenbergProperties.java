package com.fu.math_copilot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Gotenberg PDF 转换配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "export.gotenberg")
public class ExportGotenbergProperties {

    private boolean enabled = true;

    private String baseUrl = "http://localhost:3000";

    private int connectTimeoutMs = 5000;

    private int readTimeoutMs = 120000;

    /**
     * 与 exam_paper.ftl 中 data-katex-ready 配合
     */
    private String waitForExpression = "document.body.getAttribute(\"data-katex-ready\") === \"true\"";

    /**
     * KaTeX CDN 加载兜底等待（如 2s）
     */
    private String waitDelay = "2s";

    private boolean printBackground = true;

    private double paperWidth = 8.27;

    private double paperHeight = 11.7;

    private double marginTop = 0.39;

    private double marginBottom = 0.39;

    private double marginLeft = 0.59;

    private double marginRight = 0.59;
}
