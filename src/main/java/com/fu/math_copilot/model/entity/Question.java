package com.fu.math_copilot.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 题目
 *
 * @TableName question
 */
@TableName(value = "question")
@Data
public class Question implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 题干（LaTeX）
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
     * 客观题选项（JSON）
     */
    private String options;

    /**
     * 标准答案（LaTeX 或选项 key）
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

    private Long userId;

    private Date editTime;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
