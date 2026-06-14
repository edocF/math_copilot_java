package com.fu.math_copilot.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 题目收藏
 *
 * @TableName question_favorite
 */
@TableName(value = "question_favorite")
@Data
public class QuestionFavorite implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long questionId;

    private Date createTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
