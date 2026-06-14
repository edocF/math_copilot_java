package com.fu.math_copilot.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 选择题选项（解析后）
 */
@Data
public class QuestionOptionVO implements Serializable {

    private String key;

    private String content;

    private static final long serialVersionUID = 1L;
}
