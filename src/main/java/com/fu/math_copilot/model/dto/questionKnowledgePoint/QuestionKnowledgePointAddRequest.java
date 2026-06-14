package com.fu.math_copilot.model.dto.questionKnowledgePoint;

import lombok.Data;

import java.io.Serializable;

@Data
public class QuestionKnowledgePointAddRequest implements Serializable {

    /**
     * 题目 id
     */
    private Long questionId;

    /**
     * 知识点 id
     */
    private Long knowledgePointId;

    private static final long serialVersionUID = 1L;
}
