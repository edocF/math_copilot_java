package com.fu.math_copilot.listener;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.fu.math_copilot.config.AsyncConfig;
import com.fu.math_copilot.event.ExportTaskSubmittedEvent;
import com.fu.math_copilot.service.ExportTaskService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExportTaskEventListener {
    private final ExportTaskService exportTaskService;

    @Async(AsyncConfig.EXPORT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onExportTaskSubmitted(ExportTaskSubmittedEvent event) {
        Long taskId = event.getTaskId();
        log.info("收到导出任务提交事件, taskId={}", taskId);
        exportTaskService.executeExportAsync(taskId);
    }
}
