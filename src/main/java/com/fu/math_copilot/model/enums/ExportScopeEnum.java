package com.fu.math_copilot.model.enums;

import org.apache.commons.lang3.ObjectUtils;

/**
 * 导出内容范围
 */
public enum ExportScopeEnum {

    QUESTION("仅题目", "question"),
    ANSWER("题+答案解析", "answer"),
    BOTH("题目与答案分页", "both");

    private final String text;
    private final String value;

    ExportScopeEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    public static ExportScopeEnum getEnumByValue(String value) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        for (ExportScopeEnum item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        return null;
    }

    public String getValue() {
        return value;
    }

    public String getText() {
        return text;
    }
}
