package com.fu.math_copilot.model.enums;

/**
 * 组卷方式（当前仅支持人工选题）
 */
public enum ExamGenerateTypeEnum {

    MANUAL("手动选题", "manual");

    private final String text;
    private final String value;

    ExamGenerateTypeEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public String getText() {
        return text;
    }
}
