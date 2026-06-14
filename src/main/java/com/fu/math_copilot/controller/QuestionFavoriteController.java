package com.fu.math_copilot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fu.math_copilot.common.BaseResponse;
import com.fu.math_copilot.common.ErrorCode;
import com.fu.math_copilot.common.ResultUtils;
import com.fu.math_copilot.exception.ThrowUtils;
import com.fu.math_copilot.model.dto.questionFavorite.QuestionFavoriteAddRequest;
import com.fu.math_copilot.model.dto.questionFavorite.QuestionFavoriteQueryRequest;
import com.fu.math_copilot.model.dto.questionFavorite.QuestionFavoriteRemoveRequest;
import com.fu.math_copilot.model.vo.QuestionFavoriteVO;
import com.fu.math_copilot.service.QuestionFavoriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/questionFavorite")
@Slf4j
@RequiredArgsConstructor
public class QuestionFavoriteController {

    private final QuestionFavoriteService questionFavoriteService;

    /**
     * 收藏题目（幂等）
     */
    @PostMapping("/add")
    public BaseResponse<Long> addFavorite(@RequestBody QuestionFavoriteAddRequest addRequest) {
        ThrowUtils.throwIf(addRequest == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(questionFavoriteService.addFavorite(addRequest));
    }

    /**
     * 取消收藏（按 questionId 物理删除）
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> removeFavorite(@RequestBody QuestionFavoriteRemoveRequest removeRequest) {
        ThrowUtils.throwIf(removeRequest == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(questionFavoriteService.removeFavorite(removeRequest));
    }

    /**
     * 分页查询我的收藏
     */
    @PostMapping("/list/page")
    public BaseResponse<Page<QuestionFavoriteVO>> listMyFavorite(
            @RequestBody QuestionFavoriteQueryRequest queryRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(questionFavoriteService.listMyFavoritePage(queryRequest));
    }

    /**
     * 是否已收藏某题
     */
    @GetMapping("/get")
    public BaseResponse<Boolean> isFavorite(@RequestParam Long questionId) {
        ThrowUtils.throwIf(questionId == null || questionId <= 0, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(questionFavoriteService.isFavorite(questionId));
    }
}
