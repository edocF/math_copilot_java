package com.fu.math_copilot.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fu.math_copilot.model.dto.questionBankQuestion.QuestionBankQuestionBatchAddRequest;
import com.fu.math_copilot.model.dto.questionBankQuestion.QuestionBankQuestionBatchRemoveRequest;
import com.fu.math_copilot.model.dto.questionBankQuestion.QuestionBankQuestionQueryRequest;
import com.fu.math_copilot.model.entity.QuestionBankQuestion;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;


/**
* @author lenovo
* @description 针对表【question_bank_question(题库题目)】的数据库操作Service
* @createDate 2026-03-15 18:48:45
*/
public interface QuestionBankQuestionService extends IService<QuestionBankQuestion> {

     Set<Long> getQuestionIdsByQuestionBankId(Long id);

    void validate(QuestionBankQuestion questionBankQuestion, boolean add);

    Wrapper<QuestionBankQuestion> getQueryWrapper(QuestionBankQuestionQueryRequest questionBankQuestionQueryRequest);

    void QuestionBatchAddToQuestionBank(QuestionBankQuestionBatchAddRequest questionBankQuestionBatchAddRequest);

    @Transactional(rollbackFor = Exception.class)
    void batchAddQuestionsToBankInner(List<QuestionBankQuestion> questionBankQuestions);

    void QuestionBatchRemoveFromQuestionBank(QuestionBankQuestionBatchRemoveRequest questionBankQuestionBatchRemoveRequest);
}
