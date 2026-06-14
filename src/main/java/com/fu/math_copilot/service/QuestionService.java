package com.fu.math_copilot.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fu.math_copilot.model.dto.question.QuestionQueryRequest;
import com.fu.math_copilot.model.entity.Question;
import com.fu.math_copilot.model.vo.QuestionJudgeVO;
import com.fu.math_copilot.model.vo.QuestionVO;


/**
* @author lenovo
* @description 针对表【question(题目)】的数据库操作Service
* @createDate 2026-03-15 18:48:45
*/
public interface QuestionService extends IService<Question> {

    void validQuestion(Question question, boolean add);

    Wrapper<Question> getQueryWrapper(QuestionQueryRequest questionQueryRequest);

    Page<Question> getQuestionByPages(QuestionQueryRequest questionQueryRequest);

    Page<QuestionVO> getQuestionVOByPages(Page<Question> questionByPages);

    QuestionVO getQuestionVO(Question question);

    Page<Question> searchFromEs(QuestionQueryRequest questionQueryRequest);

    /**
     * 客观题自动判题
     */
    QuestionJudgeVO judgeQuestion(Long questionId, String userAnswer);

    Question getQuestionFromCache(Long id);
    void evictQuestionCache(Long id);
}
