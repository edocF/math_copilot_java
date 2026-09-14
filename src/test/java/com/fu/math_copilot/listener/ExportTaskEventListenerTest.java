package com.fu.math_copilot.listener;

import com.fu.math_copilot.event.ExportTaskSubmittedEvent;
import com.fu.math_copilot.mq.ExportMessagePublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExportTaskEventListenerTest {

    @Mock
    private ExportMessagePublisher publisher;

    @Test
    void committedTaskIsPublishedInsteadOfExecutedLocally() {
        ExportTaskEventListener listener = new ExportTaskEventListener(publisher);

        listener.onExportTaskSubmitted(new ExportTaskSubmittedEvent(this, 42L));

        verify(publisher).sendMain(42L);
    }
}
