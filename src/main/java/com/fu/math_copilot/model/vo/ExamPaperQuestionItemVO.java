package com.fu.math_copilot.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 试卷中单题（实时题面或导出定格）
 */
@Data
public class ExamPaperQuestionItemVO implements Serializable {

    private Integer sortNo;

    private Long questionId;

    private String questionType;

    private Integer difficulty;

    private Integer score;

    private String content;

    private String options;

    private List<QuestionOptionVO> optionList;

    private String answer;

    private String analysis;

    private String picture;

    private static final long serialVersionUID = 1L;
}
