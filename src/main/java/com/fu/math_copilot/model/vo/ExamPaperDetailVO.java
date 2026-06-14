package com.fu.math_copilot.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 试卷详情（预览 / 导出渲染入参，方案 C）
 */
@Data
public class ExamPaperDetailVO implements Serializable {

    private Long paperId;

    private String title;

    private String generateType;

    private Integer questionCount;

    private Integer totalScore;

    private String contentScope;

    private Date exportedAt;

    private List<Long> missingQuestionIds;

    private List<ExamPaperQuestionItemVO> questions;

    private static final long serialVersionUID = 1L;
}
