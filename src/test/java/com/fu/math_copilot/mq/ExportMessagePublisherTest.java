package com.fu.math_copilot.mq;

import com.fu.math_copilot.config.ExportRabbitConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExportMessagePublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private ExportMessagePublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new ExportMessagePublisher(rabbitTemplate);
    }

    @Test
    void mainMessageIsPersistentAndCorrelatedByTaskId() throws Exception {
        ArgumentCaptor<MessagePostProcessor> processor =
                ArgumentCaptor.forClass(MessagePostProcessor.class);
        ArgumentCaptor<CorrelationData> correlation =
                ArgumentCaptor.forClass(CorrelationData.class);

        publisher.sendMain(42L);

        verify(rabbitTemplate).convertAndSend(
                eq(ExportRabbitConfig.MAIN_EXCHANGE),
                eq(ExportRabbitConfig.MAIN_ROUTING_KEY),
                eq(new ExportTaskMessage(42L)),
                processor.capture(), correlation.capture());
        Message message = processor.getValue().postProcessMessage(new Message(new byte[0]));
        assertEquals(MessageDeliveryMode.PERSISTENT,
                message.getMessageProperties().getDeliveryMode());
        assertEquals("42", message.getMessageProperties().getCorrelationId());
        assertEquals("42", correlation.getValue().getId());
    }
}
