package com.fu.math_copilot.model.enums;

import org.apache.commons.lang3.ObjectUtils;

/**
 * 导出任务状态
 */
public enum ExportStatusEnum {

    PENDING("待处理", "pending"),
    PROCESSING("处理中", "processing"),
    SUCCESS("成功", "success"),
    FAILED("失败", "failed");

    private final String text;
    private final String value;

    ExportStatusEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    public static ExportStatusEnum getEnumByValue(String value) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        for (ExportStatusEnum item : values()) {
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
