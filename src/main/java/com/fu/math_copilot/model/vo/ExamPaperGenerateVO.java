package com.fu.math_copilot.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 组卷结果
 */
@Data
public class ExamPaperGenerateVO implements Serializable {

    private Long paperId;

    private Integer questionCount;

    private Integer totalScore;

    private ExamPaperDetailVO detail;

    private static final long serialVersionUID = 1L;
}
