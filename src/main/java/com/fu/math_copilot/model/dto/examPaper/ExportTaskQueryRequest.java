package com.fu.math_copilot.model.dto.examPaper;

import com.fu.math_copilot.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 我的导出任务分页查询
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class ExportTaskQueryRequest extends PageRequest implements Serializable {

    private Long paperId;

    private String status;

    private static final long serialVersionUID = 1L;
}
