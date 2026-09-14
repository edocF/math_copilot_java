package com.fu.math_copilot.service;

import cn.hutool.core.util.StrUtil;
import com.fu.math_copilot.common.ErrorCode;
import com.fu.math_copilot.config.ExportMqProperties;
import com.fu.math_copilot.exception.BusinessException;
import com.fu.math_copilot.mapper.ExportTaskMapper;
import com.fu.math_copilot.model.entity.ExportTask;
import com.fu.math_copilot.model.enums.ExportStatusEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExportTaskStateManager {

    public enum FailureDisposition {
        RETRY,
        DEAD
    }

    private final ExportTaskMapper exportTaskMapper;
    private final ExportMqProperties properties;

    public boolean tryMarkProcessing(Long taskId) {
        return taskId != null && taskId > 0 && exportTaskMapper.claimPending(taskId) == 1;
    }

    @Transactional(rollbackFor = Exception.class)
    public FailureDisposition recordFailure(Long taskId, String errorMessage) {
        ExportTask task = exportTaskMapper.selectById(taskId);
        if (task == null || !ExportStatusEnum.PROCESSING.getValue().equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "导出任务状态已变更");
        }

        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        int nextRetryCount = retryCount + 1;
        String safeError = StrUtil.sub(
                StrUtil.blankToDefault(errorMessage, "导出失败，请稍后重试"), 0, 500);

        int affected;
        FailureDisposition disposition;
        if (nextRetryCount < properties.getMaxRetries()) {
            affected = exportTaskMapper.rescheduleFromProcessing(taskId, safeError);
            disposition = FailureDisposition.RETRY;
        } else {
            affected = exportTaskMapper.markFailedFromProcessing(taskId, safeError);
            disposition = FailureDisposition.DEAD;
        }
        if (affected != 1) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "导出任务状态并发冲突");
        }
        return disposition;
    }

    public List<Long> recoverTimedOutProcessing(Date cutoff, int limit) {
        int boundedLimit = boundedLimit(limit);
        List<Long> candidates = exportTaskMapper.findIdsByStatusBefore(
                ExportStatusEnum.PROCESSING.getValue(), cutoff, boundedLimit);
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> recovered = new ArrayList<>();
        for (Long taskId : candidates) {
            if (exportTaskMapper.resetTimedOutProcessing(taskId, cutoff) == 1) {
                recovered.add(taskId);
            }
        }
        return recovered;
    }

    public List<Long> findStalePending(Date cutoff, int limit) {
        List<Long> taskIds = exportTaskMapper.findIdsByStatusBefore(
                ExportStatusEnum.PENDING.getValue(), cutoff, boundedLimit(limit));
        return taskIds == null ? Collections.emptyList() : taskIds;
    }

    public void touchPendingDispatch(Long taskId) {
        exportTaskMapper.touchPendingDispatch(taskId);
    }

    private int boundedLimit(int requestedLimit) {
        return Math.min(Math.max(requestedLimit, 1), properties.getRecoveryBatchSize());
    }
}
