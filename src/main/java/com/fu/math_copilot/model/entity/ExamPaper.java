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
 * 试卷（组卷实例）
 */
@TableName(value = "exam_paper")
@Data
public class ExamPaper implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private Long userId;

    private String generateType;

    private String conditionJson;

    private Integer questionCount;

    private Integer totalScore;

    private String requestNo;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
