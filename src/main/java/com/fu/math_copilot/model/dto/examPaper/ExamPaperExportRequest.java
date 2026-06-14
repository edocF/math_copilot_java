package com.fu.math_copilot.model.dto.examPaper;

import lombok.Data;

import java.io.Serializable;

/**
 * 提交导出任务
 */
@Data
public class ExamPaperExportRequest implements Serializable {

    private Long paperId;

    /**
     * 固定为 pdf；不传时默认 pdf
     */
    private String format;

    /**
     * question / answer / both
     */
    private String contentScope;

    private static final long serialVersionUID = 1L;
}
