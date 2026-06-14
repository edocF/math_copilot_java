package com.fu.math_copilot.model.dto.question;

import lombok.Data;

import java.io.Serializable;

@Data
public class QuestionAddRequest implements Serializable {


    /**
     * 题干（LaTeX）
     */
    private String content;

    /**
     * 题型：single/multiple/judge/blank/subjective
     */
    private String questionType;

    /**
     * 难度 1-5
     */
    private Integer difficulty;

    /**
     * 客观题选项（JSON 字符串）
     */
    private String options;

    /**
     * 标准答案（LaTeX 或选项 key）
     */
    private String answer;

    /**
     * 解析（LaTeX）
     */
    private String analysis;

    /**
     * 题目来源
     */
    private String source;

    /**
     * 配图 URL
     */
    private String picture;



    private static final long serialVersionUID = 1L;
}
