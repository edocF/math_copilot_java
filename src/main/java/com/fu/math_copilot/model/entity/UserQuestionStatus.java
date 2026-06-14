package com.fu.math_copilot.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户题目做题状态
 *
 * @TableName user_question_status
 */
@TableName(value = "user_question_status")
@Data
public class UserQuestionStatus implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long questionId;

    /**
     * not_done / pending / done
     */
    private String status;

    /**
     * correct / wrong，仅 status=done 时有值
     */
    private String result;

    private Date createTime;

    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
