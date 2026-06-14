package com.fu.math_copilot.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
public class QuestionVO implements Serializable {


    /**
     * id
     */
    private Long id;
    /**
     * 内容
     */
    private String content;

    /**
     * 题型：single/multiple/judge/blank/subjective
     */
    private String questionType;

    /**
     * 难度 1-5
     */
    private Integer difficulty;

    /**
     * 客观题选项（JSON 字符串，原始存储）
     */
    private String options;

    /**
     * 解析后的选项列表（single/multiple 时有值）
     */
    private List<QuestionOptionVO> optionList;

    /**
     * 推荐答案
     */
    private String answer;

    /**
     * 解析（LaTeX）
     */
    private String analysis;

    /**
     * 题目来源
     */
    private String source;

    /**
     * 配图 URL
     */
    private String picture;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 创建用户
     */
    private UserVO userVO;

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
