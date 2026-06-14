package com.fu.math_copilot.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 题目知识点关联
 *
 * @TableName question_knowledge_point
 */
@TableName(value = "question_knowledge_point")
@Data
public class QuestionKnowledgePoint implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long questionId;

    private Long knowledgePointId;

    private Long userId;

    private Date createTime;

    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
