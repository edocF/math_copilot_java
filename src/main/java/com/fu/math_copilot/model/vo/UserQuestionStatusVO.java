package com.fu.math_copilot.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class UserQuestionStatusVO implements Serializable {

    private Long id;

    private Long questionId;

    private String status;

    private String result;

    private Date createTime;

    private Date updateTime;

    /**
     * 关联题目信息（列表接口填充）
     */
    private QuestionVO questionVO;

    private static final long serialVersionUID = 1L;
}
