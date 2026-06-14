package com.fu.math_copilot.event;

import org.springframework.context.ApplicationEvent;

import lombok.Getter;

@Getter
public class ExportTaskSubmittedEvent extends ApplicationEvent {
    private final Long taskId;

    public ExportTaskSubmittedEvent(Object source, Long taskId) {
        super(source);
        this.taskId = taskId;
    }
    
}
