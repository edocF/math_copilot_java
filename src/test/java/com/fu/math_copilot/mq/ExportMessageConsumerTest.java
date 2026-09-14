package com.fu.math_copilot.mq;

import com.fu.math_copilot.service.ExportTaskService;
import com.fu.math_copilot.service.ExportTaskStateManager;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import static com.fu.math_copilot.service.ExportTaskStateManager.FailureDisposition.DEAD;
import static com.fu.math_copilot.service.ExportTaskStateManager.FailureDisposition.RETRY;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportMessageConsumerTest {

    @Mock
    private ExportTaskStateManager stateManager;
    @Mock
    private ExportTaskService exportTaskService;
    @Mock
    private ExportMessagePublisher publisher;
    @Mock
    private Channel channel;

    private ExportMessageConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ExportMessageConsumer(stateManager, exportTaskService, publisher);
    }

    @Test
    void successfulDeliveryExecutesOnceThenAcknowledges() throws Exception {
        when(stateManager.tryMarkProcessing(42L)).thenReturn(true);

        consumer.consume(new ExportTaskMessage(42L), messageWithTag(6L), channel);

        verify(exportTaskService).executeExport(42L);
        verify(channel).basicAck(6L, false);
    }

    @Test
    void duplicateDeliveryIsAcknowledgedWithoutExecution() throws Exception {
        when(stateManager.tryMarkProcessing(42L)).thenReturn(false);

        consumer.consume(new ExportTaskMessage(42L), messageWithTag(7L), channel);

        verify(exportTaskService, never()).executeExport(anyLong());
        verify(channel).basicAck(7L, false);
    }

    @Test
    void transientFailureSchedulesRetryThenAcknowledgesOriginal() throws Exception {
        when(stateManager.tryMarkProcessing(42L)).thenReturn(true);
        doThrow(new RuntimeException("temporary"))
                .when(exportTaskService).executeExport(42L);
        when(stateManager.recordFailure(eq(42L), anyString())).thenReturn(RETRY);

        consumer.consume(new ExportTaskMessage(42L), messageWithTag(8L), channel);

        verify(publisher).sendRetry(42L);
        verify(channel).basicAck(8L, false);
    }

    @Test
    void finalFailurePublishesDeadLetterThenAcknowledgesOriginal() throws Exception {
        when(stateManager.tryMarkProcessing(42L)).thenReturn(true);
        doThrow(new RuntimeException("permanent"))
                .when(exportTaskService).executeExport(42L);
        when(stateManager.recordFailure(eq(42L), anyString())).thenReturn(DEAD);

        consumer.consume(new ExportTaskMessage(42L), messageWithTag(9L), channel);

        verify(publisher).sendDead(42L);
        verify(channel).basicAck(9L, false);
    }

    @Test
    void retryPublishFailureStillAcknowledgesBecausePendingTaskCanBeRecovered() throws Exception {
        when(stateManager.tryMarkProcessing(42L)).thenReturn(true);
        doThrow(new RuntimeException("conversion failed"))
                .when(exportTaskService).executeExport(42L);
        when(stateManager.recordFailure(eq(42L), anyString())).thenReturn(RETRY);
        doThrow(new RuntimeException("broker unavailable")).when(publisher).sendRetry(42L);

        consumer.consume(new ExportTaskMessage(42L), messageWithTag(10L), channel);

        verify(channel).basicAck(10L, false);
    }

    @Test
    void invalidTaskIdIsAcknowledgedWithoutDatabaseAccess() throws Exception {
        consumer.consume(new ExportTaskMessage(null), messageWithTag(11L), channel);

        verify(stateManager, never()).tryMarkProcessing(anyLong());
        verify(channel).basicAck(11L, false);
    }

    private Message messageWithTag(long deliveryTag) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(deliveryTag);
        return new Message(new byte[0], properties);
    }
}
