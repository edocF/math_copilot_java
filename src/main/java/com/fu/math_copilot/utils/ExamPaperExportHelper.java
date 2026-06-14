package com.fu.math_copilot.utils;



import cn.hutool.core.util.StrUtil;

import com.fu.math_copilot.common.ErrorCode;

import com.fu.math_copilot.exception.BusinessException;

import com.fu.math_copilot.model.enums.ExportScopeEnum;

import com.fu.math_copilot.model.vo.ExamPaperDetailVO;

import com.fu.math_copilot.model.vo.ExamPaperQuestionItemVO;

import com.fu.math_copilot.model.vo.QuestionOptionVO;

import freemarker.template.Configuration;

import freemarker.template.Template;

import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;



import java.io.StringWriter;

import java.util.ArrayList;

import java.util.Collections;

import java.util.HashMap;

import java.util.LinkedHashMap;

import java.util.List;

import java.util.Map;



/**

 * 试卷导出 HTML 渲染

 */

@Component

@RequiredArgsConstructor

@Slf4j

public class ExamPaperExportHelper {



    private final Configuration freemarkerConfiguration;



    public String renderHtml(ExamPaperDetailVO paper) {

        try {

            Template template = freemarkerConfiguration.getTemplate("exam_paper.ftl");

            Map<String, Object> model = new HashMap<>(4);

            model.put("paper", paper);

            model.put("questions", buildQuestionViews(paper.getQuestions()));

            model.put("showAnswer", shouldShowAnswer(paper.getContentScope()));

            StringWriter writer = new StringWriter();

            template.process(model, writer);

            return writer.toString();

        } catch (Exception e) {

            log.error("试卷模板渲染失败, paperId={}", paper.getPaperId(), e);

            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "试卷模板渲染失败");

        }

    }



    private List<Map<String, Object>> buildQuestionViews(List<ExamPaperQuestionItemVO> questions) {

        if (questions == null || questions.isEmpty()) {

            return Collections.emptyList();

        }

        List<Map<String, Object>> views = new ArrayList<>(questions.size());

        for (ExamPaperQuestionItemVO question : questions) {

            Map<String, Object> view = new HashMap<>(8);

            view.put("sortNo", question.getSortNo());

            view.put("questionType", question.getQuestionType());

            view.put("content", question.getContent());

            view.put("picture", question.getPicture());

            view.put("answer", question.getAnswer());

            view.put("analysis", question.getAnalysis());

            List<QuestionOptionVO> optionList = resolveOptionList(question);

            if (!optionList.isEmpty()) {

                view.put("optionList", toFreeMarkerOptionList(optionList));

            }

            views.add(view);

        }

        return views;

    }



    private List<QuestionOptionVO> resolveOptionList(ExamPaperQuestionItemVO question) {

        if (question.getOptionList() != null && !question.getOptionList().isEmpty()) {

            return question.getOptionList();

        }

        return QuestionOptionsUtils.parseOptionList(

                question.getQuestionId(), question.getQuestionType(), question.getOptions());

    }



    private List<Map<String, String>> toFreeMarkerOptionList(List<QuestionOptionVO> optionList) {

        List<Map<String, String>> list = new ArrayList<>(optionList.size());

        for (QuestionOptionVO option : optionList) {

            Map<String, String> opt = new LinkedHashMap<>(2);

            opt.put("key", StrUtil.nullToEmpty(option.getKey()));

            opt.put("content", StrUtil.nullToEmpty(option.getContent()));

            list.add(opt);

        }

        return list;

    }



    private boolean shouldShowAnswer(String contentScope) {

        ExportScopeEnum scopeEnum = ExportScopeEnum.getEnumByValue(contentScope);

        if (scopeEnum == null) {

            return false;

        }

        return scopeEnum == ExportScopeEnum.ANSWER || scopeEnum == ExportScopeEnum.BOTH;

    }

}


