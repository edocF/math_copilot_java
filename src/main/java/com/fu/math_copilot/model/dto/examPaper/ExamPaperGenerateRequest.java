package com.fu.math_copilot.model.dto.examPaper;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 人工组卷请求
 */
@Data
public class ExamPaperGenerateRequest implements Serializable {

    private String title;

    private List<Long> questionIds;

    private Integer defaultScore;

    private String idempotencyKey;

    private static final long serialVersionUID = 1L;
}
