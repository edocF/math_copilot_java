package com.fu.math_copilot.model.dto.userQuestionStatus;

import com.fu.math_copilot.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@Data
@EqualsAndHashCode(callSuper = false)
public class UserQuestionStatusQueryRequest extends PageRequest implements Serializable {

    /**
     * 题目 id
     */
    private Long questionId;

    /**
     * not_done / pending / done
     */
    private String status;

    /**
     * correct / wrong
     */
    private String result;

    private static final long serialVersionUID = 1L;
}
