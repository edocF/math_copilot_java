package com.fu.math_copilot.model.enums;

import org.apache.commons.lang3.ObjectUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 题目题型枚举
 */
public enum QuestionTypeEnum {

    SINGLE("单选", "single"),
    MULTIPLE("多选", "multiple"),
    JUDGE("判断", "judge"),
    BLANK("填空", "blank"),
    SUBJECTIVE("解答", "subjective");

    private final String text;

    private final String value;

    QuestionTypeEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    public static List<String> getValues() {
        return Arrays.stream(values()).map(item -> item.value).collect(Collectors.toList());
    }

    public static QuestionTypeEnum getEnumByValue(String value) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        for (QuestionTypeEnum anEnum : QuestionTypeEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
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
