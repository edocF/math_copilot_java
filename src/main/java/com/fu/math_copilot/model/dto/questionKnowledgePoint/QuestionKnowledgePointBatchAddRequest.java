package com.fu.math_copilot.model.dto.questionKnowledgePoint;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class QuestionKnowledgePointBatchAddRequest implements Serializable {

    /**
     * 知识点 id
     */
    private Long knowledgePointId;

    /**
     * 题目 id 列表
     */
    private List<Long> questionIdList;

    private static final long serialVersionUID = 1L;
}
