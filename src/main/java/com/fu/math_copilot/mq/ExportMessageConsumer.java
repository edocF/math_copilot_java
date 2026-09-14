package com.fu.math_copilot.mq;

import cn.hutool.core.util.StrUtil;
import com.fu.math_copilot.config.ExportRabbitConfig;
import com.fu.math_copilot.service.ExportTaskService;
import com.fu.math_copilot.service.ExportTaskStateManager;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExportMessageConsumer {

    private final ExportTaskStateManager stateManager;
    private final ExportTaskService exportTaskService;
    private final ExportMessagePublisher publisher;

    @RabbitListener(queues = ExportRabbitConfig.MAIN_QUEUE, ackMode = "MANUAL")
    public void consume(ExportTaskMessage payload, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        if (payload == null || payload.getTaskId() == null || payload.getTaskId() <= 0) {
            log.warn("忽略非法导出消息, deliveryTag={}", deliveryTag);
            channel.basicAck(deliveryTag, false);
            return;
        }

        Long taskId = payload.getTaskId();
        boolean claimed;
        try {
            claimed = stateManager.tryMarkProcessing(taskId);
        } catch (Exception e) {
            log.error("导出任务抢占失败，转延迟重试, taskId={}", taskId, e);
            publishRetrySafely(taskId);
            channel.basicAck(deliveryTag, false);
            return;
        }

        if (!claimed) {
            log.info("导出任务已被处理或不再可执行，确认重复消息, taskId={}", taskId);
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            exportTaskService.executeExport(taskId);
        } catch (Exception executionFailure) {
            handleExecutionFailure(taskId, executionFailure);
        }
        channel.basicAck(deliveryTag, false);
    }

    private void handleExecutionFailure(Long taskId, Exception failure) {
        String errorMessage = StrUtil.blankToDefault(
                failure.getMessage(), "导出失败，请稍后重试");
        try {
            ExportTaskStateManager.FailureDisposition disposition =
                    stateManager.recordFailure(taskId, errorMessage);
            if (disposition == ExportTaskStateManager.FailureDisposition.RETRY) {
                publishRetrySafely(taskId);
            } else {
                publishDeadSafely(taskId);
            }
        } catch (Exception stateFailure) {
            log.error("记录导出失败状态时发生异常，留待恢复任务处理, taskId={}",
                    taskId, stateFailure);
        }
    }

    private void publishRetrySafely(Long taskId) {
        try {
            publisher.sendRetry(taskId);
        } catch (Exception publishFailure) {
            log.error("导出重试消息投递失败，pending 任务将由定时恢复, taskId={}",
                    taskId, publishFailure);
        }
    }

    private void publishDeadSafely(Long taskId) {
        try {
            publisher.sendDead(taskId);
        } catch (Exception publishFailure) {
            log.error("导出死信投递失败，任务已保留 failed 状态, taskId={}",
                    taskId, publishFailure);
        }
    }
}
