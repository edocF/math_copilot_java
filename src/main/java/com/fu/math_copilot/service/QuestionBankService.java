package com.fu.math_copilot.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fu.math_copilot.model.dto.questionBank.QuestionBankAddRequest;
import com.fu.math_copilot.model.dto.questionBank.QuestionBankQueryRequest;
import com.fu.math_copilot.model.entity.QuestionBank;
import com.fu.math_copilot.model.vo.QuestionBankVO;


/**
* @author lenovo
* @description 针对表【question_bank(题库)】的数据库操作Service
* @createDate 2026-03-15 18:48:45
*/
public interface QuestionBankService extends IService<QuestionBank> {

    Long addQuestionBank(QuestionBankAddRequest questionBankAddRequest);

    void validQuestionBank(QuestionBank questionBank,boolean add);

    Wrapper<QuestionBank> getQueryWrapper(QuestionBankQueryRequest questionBankQueryRequest);

    Page<QuestionBankVO> getQuestionBankVOPage(Page<QuestionBank> pages);
}
