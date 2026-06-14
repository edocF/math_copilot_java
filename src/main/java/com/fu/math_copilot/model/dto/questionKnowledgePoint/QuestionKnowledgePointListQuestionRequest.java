package com.fu.math_copilot.model.dto.questionKnowledgePoint;

import com.fu.math_copilot.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 按知识点筛选题目（刷题页：前端只传 knowledgePointId）
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class QuestionKnowledgePointListQuestionRequest extends PageRequest implements Serializable {

    /**
     * 知识点 id（必填）
     */
    private Long knowledgePointId;

    /**
     * 是否包含子知识点下的题目，默认 true
     */
    private Boolean includeChildren;

    /**
     * 题型过滤
     */
    private String questionType;

    /**
     * 难度过滤
     */
    private Integer difficulty;

    private static final long serialVersionUID = 1L;
}
