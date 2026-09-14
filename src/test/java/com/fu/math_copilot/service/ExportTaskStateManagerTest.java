package com.fu.math_copilot.service;

import com.fu.math_copilot.config.ExportMqProperties;
import com.fu.math_copilot.exception.BusinessException;
import com.fu.math_copilot.mapper.ExportTaskMapper;
import com.fu.math_copilot.model.entity.ExportTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static com.fu.math_copilot.service.ExportTaskStateManager.FailureDisposition.DEAD;
import static com.fu.math_copilot.service.ExportTaskStateManager.FailureDisposition.RETRY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportTaskStateManagerTest {

    @Mock
    private ExportTaskMapper mapper;

    private ExportMqProperties properties;
    private ExportTaskStateManager manager;

    @BeforeEach
    void setUp() {
        properties = new ExportMqProperties();
        properties.setMaxRetries(3);
        properties.setRecoveryBatchSize(100);
        manager = new ExportTaskStateManager(mapper, properties);
    }

    @Test
    void onlyPendingTaskCanBeClaimed() {
        when(mapper.claimPending(42L)).thenReturn(1);
        assertTrue(manager.tryMarkProcessing(42L));

        when(mapper.claimPending(43L)).thenReturn(0);
        assertFalse(manager.tryMarkProcessing(43L));
    }

    @Test
    void failureBeforeLimitReturnsTaskToRetry() {
        ExportTask task = processingTask(42L, 0);
        when(mapper.selectById(42L)).thenReturn(task);
        when(mapper.rescheduleFromProcessing(eq(42L), any(String.class))).thenReturn(1);

        assertEquals(RETRY, manager.recordFailure(42L, "temporary"));
        verify(mapper).rescheduleFromProcessing(42L, "temporary");
    }

    @Test
    void thirdFailureBecomesTerminal() {
        ExportTask task = processingTask(42L, 2);
        when(mapper.selectById(42L)).thenReturn(task);
        when(mapper.markFailedFromProcessing(eq(42L), any(String.class))).thenReturn(1);

        assertEquals(DEAD, manager.recordFailure(42L, "gotenberg unavailable"));
        verify(mapper).markFailedFromProcessing(42L, "gotenberg unavailable");
    }

    @Test
    void stateRaceDuringFailureIsRejected() {
        ExportTask task = processingTask(42L, 0);
        when(mapper.selectById(42L)).thenReturn(task);
        when(mapper.rescheduleFromProcessing(eq(42L), any(String.class))).thenReturn(0);

        assertThrows(BusinessException.class,
                () -> manager.recordFailure(42L, "temporary"));
    }

    @Test
    void recoveryReturnsOnlyTasksResetByThisWorkerAndHonorsBatchLimit() {
        properties.setRecoveryBatchSize(2);
        Date cutoff = new Date(1_000L);
        when(mapper.findIdsByStatusBefore("processing", cutoff, 2))
                .thenReturn(Arrays.asList(1L, 2L));
        when(mapper.resetTimedOutProcessing(1L, cutoff)).thenReturn(1);
        when(mapper.resetTimedOutProcessing(2L, cutoff)).thenReturn(0);

        List<Long> recovered = manager.recoverTimedOutProcessing(cutoff, 50);

        assertEquals(Collections.singletonList(1L), recovered);
        verify(mapper).findIdsByStatusBefore("processing", cutoff, 2);
    }

    @Test
    void stalePendingQueryUsesBoundedLimit() {
        properties.setRecoveryBatchSize(5);
        Date cutoff = new Date(2_000L);
        when(mapper.findIdsByStatusBefore("pending", cutoff, 1))
                .thenReturn(Collections.singletonList(7L));

        assertEquals(Collections.singletonList(7L),
                manager.findStalePending(cutoff, 0));
        verify(mapper).findIdsByStatusBefore("pending", cutoff, 1);
    }

    private ExportTask processingTask(Long id, Integer retryCount) {
        ExportTask task = new ExportTask();
        task.setId(id);
        task.setStatus("processing");
        task.setRetryCount(retryCount);
        return task;
    }
}
