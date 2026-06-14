package com.fu.math_copilot.model.dto.questionBankQuestion;

import lombok.Data;

import java.io.Serializable;

@Data
public class QuestionBankQuestionAddRequest implements Serializable {

    /**
     * 题库 id
     */
    private Long questionBankId;

    /**
     * 题目 id
     */
    private Long questionId;

    private static final long serialVersionUID = 1L;
}
