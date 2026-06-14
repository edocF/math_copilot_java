package com.fu.math_copilot.controller;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fu.math_copilot.annotation.AuthCheck;
import com.fu.math_copilot.common.BaseResponse;
import com.fu.math_copilot.common.DeleteRequest;
import com.fu.math_copilot.common.ErrorCode;
import com.fu.math_copilot.common.ResultUtils;
import com.fu.math_copilot.constant.UserConstant;
import com.fu.math_copilot.exception.ThrowUtils;
import com.fu.math_copilot.model.dto.questionBank.QuestionBankAddRequest;
import com.fu.math_copilot.model.dto.questionBank.QuestionBankDetailRequest;
import com.fu.math_copilot.model.dto.questionBank.QuestionBankQueryRequest;
import com.fu.math_copilot.model.dto.questionBank.QuestionBankUpdateRequest;
import com.fu.math_copilot.model.entity.Question;
import com.fu.math_copilot.model.entity.QuestionBank;
import com.fu.math_copilot.model.vo.QuestionBankVO;
import com.fu.math_copilot.service.QuestionBankQuestionService;
import com.fu.math_copilot.service.QuestionBankService;
import com.fu.math_copilot.service.QuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Set;

@RestController()
@Slf4j
@RequestMapping("/questionBank")
@RequiredArgsConstructor
public class QuestionBankController {
    private final QuestionBankService questionBankService;
    private final QuestionService questionService;
    private final QuestionBankQuestionService questionBankQuestionService;

    /**
     * 获取题库详情
     * @param questionBankDetailRequest
     * @return
     */
    @PostMapping("/get/vo")
    public BaseResponse<QuestionBankVO> getQuestionBankVO(@RequestBody QuestionBankDetailRequest questionBankDetailRequest)
        {
           ThrowUtils.throwIf(questionBankDetailRequest == null, ErrorCode.PARAMS_ERROR);
           ThrowUtils.throwIf(questionBankDetailRequest.getId() == null || questionBankDetailRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
           // 获取题库
           QuestionBank questionBank = questionBankService.getById(questionBankDetailRequest.getId());
           ThrowUtils.throwIf(questionBank == null, ErrorCode.NOT_FOUND_ERROR);
           //
           QuestionBankVO questionBankVO = BeanUtil.copyProperties(questionBank, QuestionBankVO.class);
           // 获取题目ID
            Set<Long> questionIds = questionBankQuestionService.getQuestionIdsByQuestionBankId(questionBank.getId());
            int current = questionBankDetailRequest.getCurrent();
            int pageSize = questionBankDetailRequest.getPageSize();
            // 获取题目
            Wrapper<Question> wrapper = new QueryWrapper<Question>().in("id", questionIds.isEmpty() ? Collections.singleton(-1L) : questionIds);
            Page<Question> questionPage = questionService.page(new Page<>(current, pageSize), wrapper);
            questionBankVO.setQuestionPage(questionPage);
            return ResultUtils.success(questionBankVO);
    }

    /**
     * 分页查询题库(封装好的)
     * @param questionBankQueryRequest
     * @return
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<QuestionBankVO>> listQuestionBankVOByPage(@RequestBody QuestionBankQueryRequest questionBankQueryRequest)
    {
           ThrowUtils.throwIf(questionBankQueryRequest == null, ErrorCode.PARAMS_ERROR);
           int current = questionBankQueryRequest.getCurrent();
           int pageSize = questionBankQueryRequest.getPageSize();
           Page<QuestionBank> pages = questionBankService.page(new Page<>(current, pageSize),
                   questionBankService.getQueryWrapper(questionBankQueryRequest));
           return ResultUtils.success( questionBankService.getQuestionBankVOPage(pages));
    }
    /**
     * 分页查询题库(管理员)
     * @param questionBankQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<QuestionBank>> listQuestionBankByPage(@RequestBody QuestionBankQueryRequest questionBankQueryRequest)
    {
           ThrowUtils.throwIf(questionBankQueryRequest == null, ErrorCode.PARAMS_ERROR);
           int current = questionBankQueryRequest.getCurrent();
           int pageSize = questionBankQueryRequest.getPageSize();
           Page<QuestionBank> pages = questionBankService.page(new Page<>(current, pageSize),
                   questionBankService.getQueryWrapper(questionBankQueryRequest));
           return ResultUtils.success(pages);
    }

    /**
     * 添加题库
     * @param questionBankAddRequest
     * @return
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addQuestionBank(@RequestBody QuestionBankAddRequest questionBankAddRequest)
    {
        ThrowUtils.throwIf(questionBankAddRequest == null, ErrorCode.PARAMS_ERROR);
        Long id = questionBankService.addQuestionBank(questionBankAddRequest);
        return ResultUtils.success(id);
    }
    /**
     * 删除题库
     * @param  deleteRequest
     * @return
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteQuestionBank(@RequestBody DeleteRequest deleteRequest)
    {
        ThrowUtils.throwIf(deleteRequest == null||deleteRequest.getId() == null|| deleteRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        QuestionBank questionBank = questionBankService.getById(deleteRequest.getId());
        ThrowUtils.throwIf(questionBank == null, ErrorCode.NOT_FOUND_ERROR);
        boolean res = questionBankService.removeById(deleteRequest.getId());
        ThrowUtils.throwIf(!res, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }
    /**
     * 更新题库
     * @param questionBankUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateQuestionBank(@RequestBody QuestionBankUpdateRequest questionBankUpdateRequest)
    {
           if (questionBankUpdateRequest == null||questionBankUpdateRequest.getId() == null|| questionBankUpdateRequest.getId() <= 0) {
               ThrowUtils.throwIf(true, ErrorCode.PARAMS_ERROR);
           }
        QuestionBank questionBank = BeanUtil.copyProperties(questionBankUpdateRequest, QuestionBank.class);
           // 校验
           questionBankService.validQuestionBank(questionBank,false);
           //是否存在
        QuestionBank old = questionBankService.getById(questionBank.getId());
        ThrowUtils.throwIf(old == null, ErrorCode.NOT_FOUND_ERROR);
        // 更新
        boolean result = questionBankService.updateById(questionBank);
           ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
           return ResultUtils.success(true);
    }


}
