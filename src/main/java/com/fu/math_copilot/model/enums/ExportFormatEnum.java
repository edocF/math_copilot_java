package com.fu.math_copilot.model.enums;

import org.apache.commons.lang3.ObjectUtils;

/**
 * 导出格式
 */
public enum ExportFormatEnum {

    PDF("PDF", "pdf");

    private final String text;
    private final String value;

    ExportFormatEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    public static ExportFormatEnum getEnumByValue(String value) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        for (ExportFormatEnum item : values()) {
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
