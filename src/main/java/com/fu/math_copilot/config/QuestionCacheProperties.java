package com.fu.math_copilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@ConfigurationProperties(prefix = "cache.question")
@Data
@Component
public class QuestionCacheProperties {

        /** 无 Redis 或压测对比时可关 */
        private boolean enabled = true;

        private long ttlSeconds;
        private long ttlJitterSeconds;
        private long nullTtlSeconds;
    
}
