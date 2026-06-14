package com.fu.math_copilot.model.dto.questionKnowledgePoint;

import com.fu.math_copilot.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@Data
@EqualsAndHashCode(callSuper = false)
public class QuestionKnowledgePointQueryRequest extends PageRequest implements Serializable {

    private Long id;

    private Long questionId;

    private Long knowledgePointId;

    private Long userId;

    private static final long serialVersionUID = 1L;
}
