package com.fu.math_copilot.model.dto.question;

import lombok.Data;

import java.io.Serializable;

/**
 * 客观题判题请求
 */
@Data
public class QuestionJudgeRequest implements Serializable {

    /**
     * 题目 id
     */
    private Long questionId;

    /**
     * 用户作答
     */
    private String userAnswer;

    private static final long serialVersionUID = 1L;
}
