package com.fu.math_copilot.model.dto.questionBankQuestion;

import com.fu.math_copilot.common.PageRequest;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class QuestionBankQuestionQueryRequest extends PageRequest implements Serializable {
    /**
     * id
     */
    private Long id;

    /**
     * 题库 id
     */
    private Long questionBankId;

    /**
     * 题目 id
     */
    private Long questionId;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    private static final long serialVersionUID = 1L;
}
