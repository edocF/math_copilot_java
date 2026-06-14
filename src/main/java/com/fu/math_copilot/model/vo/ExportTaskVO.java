package com.fu.math_copilot.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 导出任务 VO
 */
@Data
public class ExportTaskVO implements Serializable {

    private Long id;

    private Long paperId;

    private String format;

    private String contentScope;

    private String status;

    private Integer progress;

    private String fileUrl;

    private String errorMsg;

    private Date createTime;

    private Date updateTime;

    private static final long serialVersionUID = 1L;
}
