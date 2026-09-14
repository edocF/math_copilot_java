package com.fu.math_copilot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "export.mq")
public class ExportMqProperties {

    private long retryDelayMillis = 30_000L;

    private int maxRetries = 3;

    private long processingTimeoutMillis = 10 * 60_000L;

    private long pendingRepublishMillis = 60_000L;

    private long recoveryFixedDelayMillis = 60_000L;

    private int recoveryBatchSize = 100;
}
