package com.fu.math_copilot.job.cycle;

import com.fu.math_copilot.config.ExportMqProperties;
import com.fu.math_copilot.mq.ExportMessagePublisher;
import com.fu.math_copilot.service.ExportTaskStateManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportTaskRecoveryJobTest {

    @Mock
    private ExportTaskStateManager stateManager;
    @Mock
    private ExportMessagePublisher publisher;

    private ExportMqProperties properties;
    private ExportTaskRecoveryJob job;

    @BeforeEach
    void setUp() {
        properties = new ExportMqProperties();
        properties.setRecoveryBatchSize(100);
        properties.setProcessingTimeoutMillis(600_000L);
        properties.setPendingRepublishMillis(60_000L);
        job = new ExportTaskRecoveryJob(stateManager, publisher, properties);
    }

    @Test
    void timedOutAndStaleTasksShareOneBoundedBatch() {
        when(stateManager.recoverTimedOutProcessing(any(Date.class), eq(100)))
                .thenReturn(Arrays.asList(1L, 2L));
        when(stateManager.findStalePending(any(Date.class), eq(98)))
                .thenReturn(Collections.singletonList(3L));

        job.recover();

        verify(publisher).sendMain(1L);
        verify(publisher).sendMain(2L);
        verify(publisher).sendMain(3L);
        verify(stateManager).touchPendingDispatch(1L);
        verify(stateManager).touchPendingDispatch(2L);
        verify(stateManager).touchPendingDispatch(3L);
    }

    @Test
    void onePublishFailureDoesNotBlockLaterRecoveries() {
        when(stateManager.recoverTimedOutProcessing(any(Date.class), eq(100)))
                .thenReturn(Arrays.asList(1L, 2L));
        when(stateManager.findStalePending(any(Date.class), eq(98)))
                .thenReturn(Collections.emptyList());
        doThrow(new RuntimeException("broker unavailable")).when(publisher).sendMain(1L);

        job.recover();

        verify(publisher).sendMain(2L);
        verify(stateManager).touchPendingDispatch(2L);
    }

    @Test
    void successfulPublishIsRecordedOnlyAfterSending() {
        when(stateManager.recoverTimedOutProcessing(any(Date.class), eq(100)))
                .thenReturn(Collections.singletonList(1L));
        when(stateManager.findStalePending(any(Date.class), eq(99)))
                .thenReturn(Collections.emptyList());

        job.recover();

        InOrder order = inOrder(publisher, stateManager);
        order.verify(publisher).sendMain(1L);
        order.verify(stateManager).touchPendingDispatch(1L);
    }
}
