package com.fu.math_copilot.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fu.math_copilot.model.enums.QuestionTypeEnum;
import com.fu.math_copilot.model.vo.QuestionOptionVO;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 题目 options 字段解析（JSON 数组 / 二次编码 / Map 形态）
 */
@Slf4j
public final class QuestionOptionsUtils {

    private QuestionOptionsUtils() {
    }

    public static boolean isChoiceType(String questionType) {
        if (StrUtil.isBlank(questionType)) {
            return false;
        }
        String type = questionType.trim();
        return QuestionTypeEnum.SINGLE.getValue().equals(type)
                || QuestionTypeEnum.MULTIPLE.getValue().equals(type);
    }

    public static List<QuestionOptionVO> parseOptionList(String questionType, String optionsJson) {
        return parseOptionList(null, questionType, optionsJson);
    }

    public static List<QuestionOptionVO> parseOptionList(Long questionId, String questionType, String optionsJson) {
        if (!isChoiceType(questionType) || StrUtil.isBlank(optionsJson)) {
            return Collections.emptyList();
        }
        String normalized = unwrapEncodedJson(optionsJson.trim());
        List<QuestionOptionVO> fromArray = parseArray(normalized);
        if (!fromArray.isEmpty()) {
            return fromArray;
        }
        List<QuestionOptionVO> fromMap = parseMap(normalized);
        if (!fromMap.isEmpty()) {
            return fromMap;
        }
        // LaTeX 反斜杠未按 JSON 转义（如 \dfrac 中的 \d）时再尝试修复
        String sanitized = sanitizeInvalidJsonEscapes(normalized);
        if (!sanitized.equals(normalized)) {
            fromArray = parseArray(sanitized);
            if (!fromArray.isEmpty()) {
                return fromArray;
            }
            fromMap = parseMap(sanitized);
            if (!fromMap.isEmpty()) {
                return fromMap;
            }
        }
        log.warn("题目选项 JSON 无法解析, questionId={}, options={}", questionId, StrUtil.sub(optionsJson, 0, 160));
        return Collections.emptyList();
    }

    private static String unwrapEncodedJson(String raw) {
        String current = raw;
        for (int i = 0; i < 2; i++) {
            if (JSONUtil.isTypeJSONArray(current) || JSONUtil.isTypeJSONObject(current)) {
                return current;
            }
            if (!JSONUtil.isTypeJSON(current)) {
                break;
            }
            try {
                Object parsed = JSONUtil.parse(current);
                if (parsed instanceof String) {
                    current = ((String) parsed).trim();
                } else {
                    break;
                }
            } catch (Exception e) {
                break;
            }
        }
        return current;
    }

    private static List<QuestionOptionVO> parseArray(String json) {
        if (!JSONUtil.isTypeJSONArray(json)) {
            return Collections.emptyList();
        }
        try {
            JSONArray array = JSONUtil.parseArray(json);
            if (array == null || array.isEmpty()) {
                return Collections.emptyList();
            }
            List<QuestionOptionVO> list = new ArrayList<>(array.size());
            for (int i = 0; i < array.size(); i++) {
                Object element = array.get(i);
                if (element instanceof JSONObject) {
                    QuestionOptionVO option = fromJsonObject((JSONObject) element);
                    if (option != null) {
                        list.add(option);
                    }
                    continue;
                }
                if (element instanceof Map) {
                    QuestionOptionVO option = fromJsonObject(new JSONObject((Map<?, ?>) element));
                    if (option != null) {
                        list.add(option);
                    }
                }
            }
            return list;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static List<QuestionOptionVO> parseMap(String json) {
        if (!JSONUtil.isTypeJSONObject(json)) {
            return Collections.emptyList();
        }
        try {
            JSONObject object = JSONUtil.parseObj(json);
            if (object == null || object.isEmpty()) {
                return Collections.emptyList();
            }
            List<QuestionOptionVO> list = new ArrayList<>(object.size());
            for (Map.Entry<String, Object> entry : object.entrySet()) {
                QuestionOptionVO option = new QuestionOptionVO();
                option.setKey(entry.getKey());
                option.setContent(entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
                list.add(option);
            }
            list.sort(Comparator.comparing(QuestionOptionVO::getKey));
            return list;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static QuestionOptionVO fromJsonObject(JSONObject obj) {
        if (obj == null || obj.isEmpty()) {
            return null;
        }
        String key = StrUtil.blankToDefault(obj.getStr("key"), obj.getStr("label"));
        String content = StrUtil.blankToDefault(obj.getStr("content"), obj.getStr("value"));
        if (StrUtil.isBlank(key) && StrUtil.isBlank(content)) {
            return null;
        }
        QuestionOptionVO option = new QuestionOptionVO();
        option.setKey(StrUtil.nullToEmpty(key));
        option.setContent(StrUtil.nullToEmpty(content));
        return option;
    }

    /**
     * 修复 JSON 字符串值内非法的反斜杠转义（LaTeX 常见：\dfrac、\Delta 等）。
     * 标准 JSON 只认 \\" \\\\ \\/ \\b \\f \\n \\r \\t \\uXXXX；\d 等会导致整段解析失败。
     */
    static String sanitizeInvalidJsonEscapes(String json) {
        if (StrUtil.isBlank(json)) {
            return json;
        }
        StringBuilder sb = new StringBuilder(json.length() + 32);
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (!inString) {
                if (c == '"') {
                    inString = true;
                }
                sb.append(c);
                continue;
            }
            if (c == '"') {
                inString = false;
                sb.append(c);
                continue;
            }
            if (c == '\\') {
                if (isValidJsonEscapeSequence(json, i)) {
                    sb.append(c);
                } else {
                    sb.append('\\').append('\\');
                }
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static boolean isValidJsonEscapeSequence(String json, int backslashIndex) {
        int nextIndex = backslashIndex + 1;
        if (nextIndex >= json.length()) {
            return false;
        }
        char next = json.charAt(nextIndex);
        if (next == 'u') {
            if (nextIndex + 4 >= json.length()) {
                return false;
            }
            for (int i = nextIndex + 1; i <= nextIndex + 4; i++) {
                if (!isHexDigit(json.charAt(i))) {
                    return false;
                }
            }
            return true;
        }
        return next == '"' || next == '\\' || next == '/'
                || next == 'b' || next == 'f' || next == 'n' || next == 'r' || next == 't';
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f')
                || (c >= 'A' && c <= 'F');
    }
}
