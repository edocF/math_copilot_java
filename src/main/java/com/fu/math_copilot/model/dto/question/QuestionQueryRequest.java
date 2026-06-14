package com.fu.math_copilot.model.dto.question;

import com.fu.math_copilot.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

@EqualsAndHashCode(callSuper = false)
@Data
public class QuestionQueryRequest extends PageRequest implements Serializable {
    /**
     * id
     */
    private Long id;

    /**
     * 内容
     */
    private String content;

    /**
     * 推荐答案
     */
    private String answer;

    /**
     * 搜索词
     */
    private String searchText;

    /**
     * 题型：single/multiple/judge/blank/subjective
     */
    private String questionType;

    /**
     * 难度 1-5
     */
    private Integer difficulty;

    /**
     * 题目来源
     */
    private String source;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 编辑时间
     */
    private Date editTime;

    /**
     * 创建时间
     */
    private Date createTime;


    private static final long serialVersionUID = 1L;
}
