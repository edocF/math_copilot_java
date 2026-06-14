package com.fu.math_copilot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fu.math_copilot.common.BaseResponse;
import com.fu.math_copilot.common.ErrorCode;
import com.fu.math_copilot.common.ResultUtils;
import com.fu.math_copilot.exception.ThrowUtils;
import com.fu.math_copilot.model.dto.userQuestionStatus.UserQuestionStatusQueryRequest;
import com.fu.math_copilot.model.dto.userQuestionStatus.UserQuestionStatusUpdateRequest;
import com.fu.math_copilot.model.vo.UserQuestionStatusVO;
import com.fu.math_copilot.service.UserQuestionStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/userQuestionStatus")
@Slf4j
@RequiredArgsConstructor
public class UserQuestionStatusController {

    private final UserQuestionStatusService userQuestionStatusService;

    /**
     * 更新当前用户对某题的做题状态（幂等 upsert）
     */
    @PostMapping("/update")
    public BaseResponse<Long> updateStatus(@RequestBody UserQuestionStatusUpdateRequest updateRequest) {
        ThrowUtils.throwIf(updateRequest == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(userQuestionStatusService.updateStatus(updateRequest));
    }

    /**
     * 查询当前用户对某题的状态（无记录时返回 not_done）
     */
    @GetMapping("/get")
    public BaseResponse<UserQuestionStatusVO> getStatus(@RequestParam Long questionId) {
        ThrowUtils.throwIf(questionId == null || questionId <= 0, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(userQuestionStatusService.getStatusByQuestionId(questionId));
    }

    /**
     * 分页查询当前用户的题目状态列表
     */
    @PostMapping("/list/page")
    public BaseResponse<Page<UserQuestionStatusVO>> listMyStatus(
            @RequestBody UserQuestionStatusQueryRequest queryRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(userQuestionStatusService.listMyStatusPage(queryRequest));
    }

    /**
     * 错题列表：status=done 且 result=wrong
     */
    @PostMapping("/list/wrong")
    public BaseResponse<Page<UserQuestionStatusVO>> listWrong(
            @RequestBody UserQuestionStatusQueryRequest queryRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(userQuestionStatusService.listWrongPage(queryRequest));
    }
}
