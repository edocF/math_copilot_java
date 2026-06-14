package com.fu.math_copilot.utils;

import cn.hutool.core.util.StrUtil;
import com.fu.math_copilot.model.enums.QuestionTypeEnum;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 客观题答案比对：单选、多选、判断、填空。
 * <p>
 * 填空题标准答案约定：
 * <ul>
 *   <li>单空：直接写答案，支持 LaTeX（如 {@code $5$}、{@code $x>2$}）</li>
 *   <li>多可接受答案：用「或」分隔（如 {@code $x\neq 1$ 或 $(-\infty,1)\cup(1,+\infty)$}）</li>
 *   <li>多空：用分号 {@code ;} 分隔，每空可再含「或」备选</li>
 * </ul>
 * 用户输入可省略 LaTeX 定界符 {@code $}，数值题 {@code 5} 与 {@code $5$} 等价。
 */
public final class QuestionAnswerJudgeUtils {

    private static final Set<String> OBJECTIVE_TYPES = new HashSet<>(Arrays.asList(
            QuestionTypeEnum.SINGLE.getValue(),
            QuestionTypeEnum.MULTIPLE.getValue(),
            QuestionTypeEnum.JUDGE.getValue(),
            QuestionTypeEnum.BLANK.getValue()
    ));

    private static final Set<String> JUDGE_TRUE_VALUES = new HashSet<>(Arrays.asList(
            "true", "t", "1", "yes", "y", "正确", "对"
    ));

    private static final Set<String> JUDGE_FALSE_VALUES = new HashSet<>(Arrays.asList(
            "false", "f", "0", "no", "n", "错误", "错"
    ));

    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^[-+]?\\d+(\\.\\d+)?$");

    private QuestionAnswerJudgeUtils() {
    }

    public static boolean isObjectiveType(String questionType) {
        return StrUtil.isNotBlank(questionType) && OBJECTIVE_TYPES.contains(questionType);
    }

    /**
     * 判断用户作答是否与标准答案一致。
     *
     * @param questionType   题型：single / multiple / judge / blank
     * @param userAnswer     用户答案
     * @param standardAnswer 标准答案
     * @return 是否答对；主观题或参数非法时返回 false
     */
    public static boolean judge(String questionType, String userAnswer, String standardAnswer) {
        if (StrUtil.isBlank(userAnswer) || StrUtil.isBlank(standardAnswer)) {
            return false;
        }
        QuestionTypeEnum typeEnum = QuestionTypeEnum.getEnumByValue(questionType);
        if (typeEnum == null) {
            return false;
        }
        switch (typeEnum) {
            case SINGLE:
                return judgeSingle(userAnswer, standardAnswer);
            case MULTIPLE:
                return judgeMultiple(userAnswer, standardAnswer);
            case JUDGE:
                return judgeTrueFalse(userAnswer, standardAnswer);
            case BLANK:
                return judgeBlank(userAnswer, standardAnswer);
            default:
                return false;
        }
    }

    private static boolean judgeSingle(String userAnswer, String standardAnswer) {
        return normalizeChoiceKey(userAnswer).equals(normalizeChoiceKey(standardAnswer));
    }

    private static boolean judgeMultiple(String userAnswer, String standardAnswer) {
        return normalizeMultipleKeys(userAnswer).equals(normalizeMultipleKeys(standardAnswer));
    }

    private static boolean judgeTrueFalse(String userAnswer, String standardAnswer) {
        String user = normalizeJudgeValue(userAnswer);
        String standard = normalizeJudgeValue(standardAnswer);
        return StrUtil.isNotBlank(user) && user.equals(standard);
    }

    private static boolean judgeBlank(String userAnswer, String standardAnswer) {
        String standard = standardAnswer.trim();

        if (containsMultiBlankDelimiter(standard)) {
            String[] stdParts = splitMultiBlank(standard);
            String[] userParts = splitMultiBlank(userAnswer.trim());
            if (stdParts.length != userParts.length) {
                return false;
            }
            for (int i = 0; i < stdParts.length; i++) {
                if (!matchBlankAlternatives(userParts[i].trim(), stdParts[i].trim())) {
                    return false;
                }
            }
            return true;
        }

        return matchBlankAlternatives(userAnswer.trim(), standard);
    }

    /**
     * 标准答案支持「或」、竖线分隔的多个可接受写法（如 LaTeX 等价表达）。
     */
    private static boolean matchBlankAlternatives(String userAnswer, String standardPart) {
        if (containsAlternativeDelimiter(standardPart)) {
            return Arrays.stream(splitAlternatives(standardPart))
                    .map(String::trim)
                    .filter(StrUtil::isNotBlank)
                    .anyMatch(alt -> equalsBlank(userAnswer, alt));
        }
        return equalsBlank(userAnswer, standardPart);
    }

    private static boolean equalsBlank(String userAnswer, String standardAnswer) {
        String userNorm = normalizeBlankContent(userAnswer);
        String stdNorm = normalizeBlankContent(standardAnswer);
        if (userNorm.equalsIgnoreCase(stdNorm)) {
            return true;
        }
        if (equalsNumeric(userNorm, stdNorm)) {
            return true;
        }
        String userCompact = compactBlank(userNorm);
        String stdCompact = compactBlank(stdNorm);
        return userCompact.equalsIgnoreCase(stdCompact);
    }

    private static String normalizeBlankContent(String value) {
        if (StrUtil.isBlank(value)) {
            return "";
        }
        String normalized = stripOuterLatexDelimiters(value.trim());
        normalized = collapseWhitespace(normalized);
        normalized = normalizeLatexForCompare(normalized);
        return normalized.trim();
    }

    private static String stripOuterLatexDelimiters(String value) {
        String result = value;
        while (result.startsWith("$") && result.endsWith("$") && result.length() >= 2) {
            result = result.substring(1, result.length() - 1).trim();
        }
        return result;
    }

    private static String collapseWhitespace(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    /**
     * 将常见 LaTeX / 数学写法归一化，便于填空比对。
     */
    private static String normalizeLatexForCompare(String value) {
        String s = value;
        s = s.replace("\\,", " ");
        s = s.replace("\\;", " ");
        s = s.replace("\\!", "");
        s = s.replace("\\left", "");
        s = s.replace("\\right", "");
        s = s.replace("\\neq", "neq");
        s = s.replace("\\ne", "neq");
        s = s.replace("≠", "neq");
        s = s.replace("!=", "neq");
        s = s.replace("\\cup", "cup");
        s = s.replace("\\cap", "cap");
        s = s.replace("\\infty", "infty");
        s = s.replace("\\dfrac", "\\frac");
        s = s.replace("\\times", "*");
        s = s.replace("\\cdot", "*");
        s = s.replace("\\leq", "<=");
        s = s.replace("\\le", "<=");
        s = s.replace("\\geq", ">=");
        s = s.replace("\\ge", ">=");
        s = s.replace("\\pm", "+-");
        return collapseWhitespace(s);
    }

    private static String compactBlank(String value) {
        return value.replace(" ", "")
                .replace("{", "")
                .replace("}", "")
                .toLowerCase(Locale.ROOT);
    }

    private static boolean equalsNumeric(String left, String right) {
        if (!NUMERIC_PATTERN.matcher(left).matches() || !NUMERIC_PATTERN.matcher(right).matches()) {
            return false;
        }
        return new BigDecimal(left).compareTo(new BigDecimal(right)) == 0;
    }

    private static String normalizeChoiceKey(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeMultipleKeys(String value) {
        return Arrays.stream(value.split("[,，;；\\s]+"))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .map(QuestionAnswerJudgeUtils::normalizeChoiceKey)
                .sorted()
                .collect(Collectors.joining(","));
    }

    private static String normalizeJudgeValue(String value) {
        if (StrUtil.isBlank(value)) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (JUDGE_TRUE_VALUES.contains(normalized)) {
            return "true";
        }
        if (JUDGE_FALSE_VALUES.contains(normalized)) {
            return "false";
        }
        return normalized;
    }

    private static boolean containsAlternativeDelimiter(String text) {
        return text.contains("或") || text.matches(".*\\s\\|\\s.*");
    }

    private static boolean containsMultiBlankDelimiter(String text) {
        return text.contains(";") || text.contains("；");
    }

    private static String[] splitAlternatives(String text) {
        if (text.contains("或")) {
            return text.split("\\s*或\\s*");
        }
        if (text.matches(".*\\s\\|\\s.*")) {
            return text.split("\\s\\|\\s");
        }
        return new String[]{text};
    }

    private static String[] splitMultiBlank(String text) {
        return text.split("[;；]");
    }
}
