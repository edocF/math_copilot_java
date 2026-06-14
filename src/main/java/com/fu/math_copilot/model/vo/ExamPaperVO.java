package com.fu.math_copilot.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 试卷列表项
 */
@Data
public class ExamPaperVO implements Serializable {

    private Long id;

    private String title;

    private String generateType;

    private Integer questionCount;

    private Integer totalScore;

    private Date createTime;

    private Date updateTime;

    private static final long serialVersionUID = 1L;
}
