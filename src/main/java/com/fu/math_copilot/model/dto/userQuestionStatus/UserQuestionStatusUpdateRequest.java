package com.fu.math_copilot.model.dto.userQuestionStatus;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserQuestionStatusUpdateRequest implements Serializable {

    /**
     * 题目 id
     */
    private Long questionId;

    /**
     * not_done / pending / done
     */
    private String status;

    /**
     * correct / wrong，仅 status=done 时填写
     */
    private String result;

    private static final long serialVersionUID = 1L;
}
