package com.fu.math_copilot.job.cycle;

import com.fu.math_copilot.config.ExportMqProperties;
import com.fu.math_copilot.mq.ExportMessagePublisher;
import com.fu.math_copilot.service.ExportTaskStateManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExportTaskRecoveryJob {

    private final ExportTaskStateManager stateManager;
    private final ExportMessagePublisher publisher;
    private final ExportMqProperties properties;

    @Scheduled(fixedDelayString = "${export.mq.recovery-fixed-delay-millis:60000}")
    public void recover() {
        int batchSize = Math.max(1, properties.getRecoveryBatchSize());
        long now = System.currentTimeMillis();
        Date processingCutoff = new Date(now - properties.getProcessingTimeoutMillis());
        List<Long> recoveredProcessing =
                stateManager.recoverTimedOutProcessing(processingCutoff, batchSize);
        for (Long taskId : recoveredProcessing) {
            publishAndTouch(taskId);
        }

        int remaining = Math.max(0, batchSize - recoveredProcessing.size());
        if (remaining == 0) {
            return;
        }
        Date pendingCutoff = new Date(now - properties.getPendingRepublishMillis());
        List<Long> stalePending = stateManager.findStalePending(pendingCutoff, remaining);
        for (Long taskId : stalePending) {
            publishAndTouch(taskId);
        }
    }

    private void publishAndTouch(Long taskId) {
        try {
            publisher.sendMain(taskId);
            stateManager.touchPendingDispatch(taskId);
        } catch (Exception e) {
            log.error("恢复导出任务投递失败，下次扫描将继续尝试, taskId={}", taskId, e);
        }
    }
}
