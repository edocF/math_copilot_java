package com.fu.math_copilot.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportRabbitConfigTest {

    @Test
    void retryQueueRoutesExpiredMessagesBackToMainQueue() {
        ExportMqProperties properties = new ExportMqProperties();
        properties.setRetryDelayMillis(30_000L);
        ExportRabbitConfig config = new ExportRabbitConfig(properties);

        Queue queue = config.exportRetryQueue();

        assertEquals(30_000L, queue.getArguments().get("x-message-ttl"));
        assertEquals(ExportRabbitConfig.MAIN_EXCHANGE,
                queue.getArguments().get("x-dead-letter-exchange"));
        assertEquals(ExportRabbitConfig.MAIN_ROUTING_KEY,
                queue.getArguments().get("x-dead-letter-routing-key"));
        assertTrue(queue.isDurable());
    }
}
