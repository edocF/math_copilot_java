package com.fu.math_copilot.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 试卷导出任务
 */
@TableName(value = "export_task")
@Data
public class ExportTask implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long paperId;

    private Long userId;

    private String format;

    private String contentScope;

    private String status;

    private Integer progress;

    private String fileUrl;

    private String fileKey;

    private String errorMsg;

    private String snapshotJson;

    private String idempotentKey;

    private Date createTime;

    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
