package com.fu.math_copilot.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class QuestionFavoriteVO implements Serializable {

    private Long id;

    private Long questionId;

    private Date createTime;

    /**
     * 收藏的题目信息
     */
    private QuestionVO questionVO;

    private static final long serialVersionUID = 1L;
}
