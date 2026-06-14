package com.fu.math_copilot.manager;

import com.fu.math_copilot.common.ErrorCode;
import com.fu.math_copilot.config.ExportGotenbergProperties;
import com.fu.math_copilot.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Gotenberg HTML → PDF
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GotenbergClient {

    private static final String HTML_CONVERT_PATH = "/forms/chromium/convert/html";

    private final CloseableHttpClient gotenbergHttpClient;
    private final ExportGotenbergProperties properties;

    public byte[] convertHtmlToPdf(String indexHtml) {
        if (!properties.isEnabled()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Gotenberg 未启用");
        }
        String url = trimTrailingSlash(properties.getBaseUrl()) + HTML_CONVERT_PATH;
        HttpPost post = new HttpPost(url);
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.setCharset(StandardCharsets.UTF_8);
        builder.addBinaryBody("files", indexHtml.getBytes(StandardCharsets.UTF_8),
                ContentType.create("text/html", StandardCharsets.UTF_8), "index.html");
        builder.addTextBody("waitForExpression", properties.getWaitForExpression(),
                ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8));
        builder.addTextBody("waitDelay", properties.getWaitDelay(),
                ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8));
        builder.addTextBody("printBackground", String.valueOf(properties.isPrintBackground()),
                ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8));
        builder.addTextBody("paperWidth", String.valueOf(properties.getPaperWidth()),
                ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8));
        builder.addTextBody("paperHeight", String.valueOf(properties.getPaperHeight()),
                ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8));
        builder.addTextBody("marginTop", String.valueOf(properties.getMarginTop()),
                ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8));
        builder.addTextBody("marginBottom", String.valueOf(properties.getMarginBottom()),
                ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8));
        builder.addTextBody("marginLeft", String.valueOf(properties.getMarginLeft()),
                ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8));
        builder.addTextBody("marginRight", String.valueOf(properties.getMarginRight()),
                ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8));
        post.setEntity(builder.build());

        try (CloseableHttpResponse response = gotenbergHttpClient.execute(post)) {
            int status = response.getStatusLine().getStatusCode();
            HttpEntity entity = response.getEntity();
            byte[] body = entity == null ? new byte[0] : EntityUtils.toByteArray(entity);
            if (status < 200 || status >= 300) {
                String err = new String(body, StandardCharsets.UTF_8);
                log.error("Gotenberg 转换失败, status={}, body={}", status, err);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "PDF 生成失败");
            }
            if (body.length == 0) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "PDF 生成结果为空");
            }
            return body;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 Gotenberg 异常, url={}", url, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "PDF 服务不可用，请确认 Gotenberg 已启动");
        }
    }

    private String trimTrailingSlash(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
