package com.fu.math_copilot.model.dto.examPaper;

import com.fu.math_copilot.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 我的试卷分页查询
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class ExamPaperQueryRequest extends PageRequest implements Serializable {

    private String title;

    private static final long serialVersionUID = 1L;
}
