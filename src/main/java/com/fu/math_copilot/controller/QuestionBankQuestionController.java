package com.fu.math_copilot.controller;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fu.math_copilot.annotation.AuthCheck;
import com.fu.math_copilot.common.BaseResponse;
import com.fu.math_copilot.common.DeleteRequest;
import com.fu.math_copilot.common.ErrorCode;
import com.fu.math_copilot.common.ResultUtils;
import com.fu.math_copilot.exception.ThrowUtils;
import com.fu.math_copilot.model.dto.questionBankQuestion.*;
import com.fu.math_copilot.model.entity.QuestionBankQuestion;
import com.fu.math_copilot.service.QuestionBankQuestionService;
import com.fu.math_copilot.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController()
@RequestMapping("/questionBankQuestion")
@Slf4j
@RequiredArgsConstructor
public class QuestionBankQuestionController {
    private final QuestionBankQuestionService questionBankQuestionService;

    /**
     * 分页获取题目题库关联列表（仅管理员）
     *
     * @param questionBankQuestionQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = "admin")
    public BaseResponse<Page<QuestionBankQuestion>> listQuestionBankQuestion(@RequestBody QuestionBankQuestionQueryRequest questionBankQuestionQueryRequest)
    {
        ThrowUtils.throwIf(questionBankQuestionQueryRequest == null, ErrorCode.PARAMS_ERROR);
        int current = questionBankQuestionQueryRequest.getCurrent();
        int pageSize = questionBankQuestionQueryRequest.getPageSize();
        Page<QuestionBankQuestion> page = questionBankQuestionService.page(new Page<>(current, pageSize), questionBankQuestionService.getQueryWrapper(questionBankQuestionQueryRequest));
        return ResultUtils.success(page);
    }
    /**
     * 添加题目题库关联
     *
     * @param questionBankQuestionAddRequest
     * @return
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = "admin")
    public BaseResponse<Long> addQuestionBankQuestion(@RequestBody QuestionBankQuestionAddRequest questionBankQuestionAddRequest)
    {
        ThrowUtils.throwIf(questionBankQuestionAddRequest == null, ErrorCode.PARAMS_ERROR);
        QuestionBankQuestion questionBankQuestion = BeanUtil.copyProperties(questionBankQuestionAddRequest, QuestionBankQuestion.class);
        questionBankQuestionService.validate(questionBankQuestion,true);
        questionBankQuestion.setUserId(UserContext.getCurrentUserId());
        boolean result = questionBankQuestionService.save(questionBankQuestion);
        ThrowUtils.throwIf(!result,ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(questionBankQuestion.getId());
    }
    /**
     * 删除题目题库关联
     *
     * @param deleteRequest
     * @return
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = "admin")
    public BaseResponse<Boolean> deleteQuestionBankQuestion(@RequestBody DeleteRequest deleteRequest)
    {
        ThrowUtils.throwIf(deleteRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(deleteRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        QuestionBankQuestion questionBankQuestion = questionBankQuestionService.getById(deleteRequest.getId());
        ThrowUtils.throwIf(questionBankQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        boolean res = questionBankQuestionService.removeById(deleteRequest.getId());
        ThrowUtils.throwIf(!res,ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }
    /**
     * 更新题目题库关联
     *
     * @param questionBankQuestionUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = "admin")
    public BaseResponse<Boolean> updateQuestionBankQuestion(@RequestBody QuestionBankQuestionUpdateRequest questionBankQuestionUpdateRequest)
    {
         ThrowUtils.throwIf(questionBankQuestionUpdateRequest == null, ErrorCode.PARAMS_ERROR);
         ThrowUtils.throwIf(questionBankQuestionUpdateRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
         QuestionBankQuestion questionBankQuestion = BeanUtil.copyProperties(questionBankQuestionUpdateRequest, QuestionBankQuestion.class);
         questionBankQuestionService.validate(questionBankQuestion,false);
        QuestionBankQuestion byId = questionBankQuestionService.getById(questionBankQuestion.getId());
        ThrowUtils.throwIf(byId == null, ErrorCode.NOT_FOUND_ERROR);
        boolean res = questionBankQuestionService.updateById(questionBankQuestion);
        ThrowUtils.throwIf(!res,ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }
    /**
     * 批量添加题目题库关联
     *
     * @param questionBankQuestionBatchAddRequest
     * @return
     */
    @PostMapping("/batch/add")
    @AuthCheck(mustRole = "admin")
    public BaseResponse<Boolean> batchAddQuestionsToBank(@RequestBody QuestionBankQuestionBatchAddRequest questionBankQuestionBatchAddRequest)
    {
        ThrowUtils.throwIf(questionBankQuestionBatchAddRequest == null, ErrorCode.PARAMS_ERROR);
        questionBankQuestionService.QuestionBatchAddToQuestionBank(questionBankQuestionBatchAddRequest);
        return ResultUtils.success(true);
    }
    /**
     * 批量删除题目题库关联
     *
     * @param questionBankQuestionBatchRemoveRequest
     * @return
     */
    @PostMapping("/batch/delete")
    @AuthCheck(mustRole = "admin")
    public BaseResponse<Boolean> batchRemoveQuestionsFromBank(@RequestBody QuestionBankQuestionBatchRemoveRequest questionBankQuestionBatchRemoveRequest)
    {
        ThrowUtils.throwIf(questionBankQuestionBatchRemoveRequest == null, ErrorCode.PARAMS_ERROR);
        questionBankQuestionService.QuestionBatchRemoveFromQuestionBank(questionBankQuestionBatchRemoveRequest);
        return ResultUtils.success(true);
    }

}
