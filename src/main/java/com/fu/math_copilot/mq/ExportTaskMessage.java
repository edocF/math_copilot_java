package com.fu.math_copilot.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExportTaskMessage implements Serializable {

    private Long taskId;

    private static final long serialVersionUID = 1L;
}
