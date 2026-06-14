package com.fu.math_copilot.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 试卷题目关联
 */
@TableName(value = "exam_paper_question")
@Data
public class ExamPaperQuestion implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long paperId;

    private Long questionId;

    private Integer sortNo;

    private Integer score;

    private String sectionType;

    private Date createTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
