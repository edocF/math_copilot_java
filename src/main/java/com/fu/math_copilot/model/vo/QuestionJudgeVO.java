package com.fu.math_copilot.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 客观题判题结果
 */
@Data
public class QuestionJudgeVO implements Serializable {

    /**
     * 是否答对
     */
    private Boolean correct;

    /**
     * correct / wrong
     */
    private String result;

    /**
     * 题型
     */
    private String questionType;

    private static final long serialVersionUID = 1L;
}
