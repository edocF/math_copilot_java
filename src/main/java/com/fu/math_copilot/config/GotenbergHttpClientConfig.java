package com.fu.math_copilot.config;

import lombok.RequiredArgsConstructor;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 调用 Gotenberg 使用的 Apache HttpClient
 */
@Configuration
@RequiredArgsConstructor
public class GotenbergHttpClientConfig {

    private final ExportGotenbergProperties properties;

    @Bean(destroyMethod = "close")
    public CloseableHttpClient gotenbergHttpClient() {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(properties.getConnectTimeoutMs())
                .setSocketTimeout(properties.getReadTimeoutMs())
                .setConnectionRequestTimeout(properties.getConnectTimeoutMs())
                .build();
        return HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();
    }
}
