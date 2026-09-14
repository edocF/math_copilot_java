package com.fu.math_copilot.mq;

import com.fu.math_copilot.config.ExportRabbitConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
@Slf4j
public class ExportMessagePublisher {

    private final RabbitTemplate rabbitTemplate;

    public ExportMessagePublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                String correlationId = correlationData == null ? null : correlationData.getId();
                log.error("RabbitMQ 导出消息未被 Broker 确认, correlationId={}, cause={}",
                        correlationId, cause);
            }
        });
        rabbitTemplate.setReturnsCallback(returned ->
                log.error("RabbitMQ 导出消息无法路由, exchange={}, routingKey={}, replyCode={}, replyText={}",
                        returned.getExchange(), returned.getRoutingKey(),
                        returned.getReplyCode(), returned.getReplyText()));
    }

    public void sendMain(Long taskId) {
        send(ExportRabbitConfig.MAIN_EXCHANGE, ExportRabbitConfig.MAIN_ROUTING_KEY, taskId);
    }

    public void sendRetry(Long taskId) {
        send(ExportRabbitConfig.RETRY_EXCHANGE, ExportRabbitConfig.RETRY_ROUTING_KEY, taskId);
    }

    public void sendDead(Long taskId) {
        send(ExportRabbitConfig.DEAD_EXCHANGE, ExportRabbitConfig.DEAD_ROUTING_KEY, taskId);
    }

    private void send(String exchange, String routingKey, Long taskId) {
        Assert.notNull(taskId, "taskId must not be null");
        String correlationId = taskId.toString();
        rabbitTemplate.convertAndSend(
                exchange,
                routingKey,
                new ExportTaskMessage(taskId),
                message -> {
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    message.getMessageProperties().setCorrelationId(correlationId);
                    return message;
                },
                new CorrelationData(correlationId));
    }
}
