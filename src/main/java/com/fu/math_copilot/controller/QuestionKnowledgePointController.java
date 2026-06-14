package com.fu.math_copilot.controller;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fu.math_copilot.annotation.AuthCheck;
import com.fu.math_copilot.common.BaseResponse;
import com.fu.math_copilot.common.DeleteRequest;
import com.fu.math_copilot.common.ErrorCode;
import com.fu.math_copilot.common.ResultUtils;
import com.fu.math_copilot.constant.UserConstant;
import com.fu.math_copilot.exception.ThrowUtils;
import com.fu.math_copilot.model.dto.questionKnowledgePoint.*;
import com.fu.math_copilot.model.entity.QuestionKnowledgePoint;
import com.fu.math_copilot.model.vo.QuestionVO;
import com.fu.math_copilot.service.QuestionKnowledgePointService;
import com.fu.math_copilot.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/questionKnowledgePoint")
@Slf4j
@RequiredArgsConstructor
public class QuestionKnowledgePointController {

    private final QuestionKnowledgePointService questionKnowledgePointService;

    /**
     * 按知识点分页查题目（刷题页：传 knowledgePointId，后端内部用 path 含子树）
     */
    @PostMapping("/list/question/page/vo")
    public BaseResponse<Page<QuestionVO>> listQuestionVOPageByKnowledgePoint(
            @RequestBody QuestionKnowledgePointListQuestionRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(questionKnowledgePointService.listQuestionVOPageByKnowledgePoint(request));
    }

    /**
     * 分页查询题目-知识点关联（管理员）
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<QuestionKnowledgePoint>> listQuestionKnowledgePoint(
            @RequestBody QuestionKnowledgePointQueryRequest queryRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        Page<QuestionKnowledgePoint> page = questionKnowledgePointService.page(
                new Page<>(queryRequest.getCurrent(), queryRequest.getPageSize()),
                questionKnowledgePointService.getQueryWrapper(queryRequest));
        return ResultUtils.success(page);
    }

    /**
     * 绑定题目与知识点
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addQuestionKnowledgePoint(
            @RequestBody QuestionKnowledgePointAddRequest addRequest) {
        ThrowUtils.throwIf(addRequest == null, ErrorCode.PARAMS_ERROR);
        QuestionKnowledgePoint relation = BeanUtil.copyProperties(addRequest, QuestionKnowledgePoint.class);
        questionKnowledgePointService.validate(relation, true);
        relation.setUserId(UserContext.getCurrentUserId());
        boolean result = questionKnowledgePointService.save(relation);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(relation.getId());
    }

    /**
     * 删除关联
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteQuestionKnowledgePoint(@RequestBody DeleteRequest deleteRequest) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null || deleteRequest.getId() <= 0,
                ErrorCode.PARAMS_ERROR);
        QuestionKnowledgePoint relation = questionKnowledgePointService.getById(deleteRequest.getId());
        ThrowUtils.throwIf(relation == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = questionKnowledgePointService.removeById(deleteRequest.getId());
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 批量绑定题目到知识点
     */
    @PostMapping("/batch/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> batchAddQuestionKnowledgePoint(
            @RequestBody QuestionKnowledgePointBatchAddRequest batchAddRequest) {
        ThrowUtils.throwIf(batchAddRequest == null, ErrorCode.PARAMS_ERROR);
        questionKnowledgePointService.batchAdd(batchAddRequest);
        return ResultUtils.success(true);
    }

    /**
     * 批量解除题目与知识点的绑定
     */
    @PostMapping("/batch/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> batchRemoveQuestionKnowledgePoint(
            @RequestBody QuestionKnowledgePointBatchRemoveRequest batchRemoveRequest) {
        ThrowUtils.throwIf(batchRemoveRequest == null, ErrorCode.PARAMS_ERROR);
        questionKnowledgePointService.batchRemove(batchRemoveRequest);
        return ResultUtils.success(true);
    }
}
