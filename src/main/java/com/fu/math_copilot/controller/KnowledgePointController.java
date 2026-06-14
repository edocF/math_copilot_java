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
import com.fu.math_copilot.model.dto.knowledgePoint.KnowledgePointAddRequest;
import com.fu.math_copilot.model.dto.knowledgePoint.KnowledgePointQueryRequest;
import com.fu.math_copilot.model.dto.knowledgePoint.KnowledgePointUpdateRequest;
import com.fu.math_copilot.model.entity.KnowledgePoint;
import com.fu.math_copilot.model.vo.KnowledgePointVO;
import com.fu.math_copilot.service.KnowledgePointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/knowledgePoint")
@Slf4j
@RequiredArgsConstructor
public class KnowledgePointController {

    private final KnowledgePointService knowledgePointService;

    /**
     * 获取知识点
     * @param id
     * @return
     */
    @GetMapping("/get/vo")
    public BaseResponse<KnowledgePointVO> getKnowledgePointVO(Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        KnowledgePoint knowledgePoint = knowledgePointService.getById(id);
        ThrowUtils.throwIf(knowledgePoint == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(knowledgePointService.getKnowledgePointVOWithoutChildren(knowledgePoint));
    }

    @PostMapping("/list/page")
    public BaseResponse<Page<KnowledgePoint>> listKnowledgePointByPage(
            @RequestBody KnowledgePointQueryRequest knowledgePointQueryRequest) {
        ThrowUtils.throwIf(knowledgePointQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long current = knowledgePointQueryRequest.getCurrent();
        long size = knowledgePointQueryRequest.getPageSize();
        Page<KnowledgePoint> page = knowledgePointService.page(new Page<>(current, size),
                knowledgePointService.getQueryWrapper(knowledgePointQueryRequest));
        return ResultUtils.success(page);
    }

    @GetMapping("/list/tree")
    public BaseResponse<List<KnowledgePointVO>> listKnowledgePointTree() {
        return ResultUtils.success(knowledgePointService.listKnowledgePointTree());
    }

    /**
     * 添加知识点
     * @param knowledgePointAddRequest
     * @return
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addKnowledgePoint(@RequestBody KnowledgePointAddRequest knowledgePointAddRequest) {
        ThrowUtils.throwIf(knowledgePointAddRequest == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(knowledgePointService.addKnowledgePoint(knowledgePointAddRequest));
    }
    /**
     * 更新知识点
     * @param knowledgePointUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateKnowledgePoint(
            @RequestBody KnowledgePointUpdateRequest knowledgePointUpdateRequest) {
        ThrowUtils.throwIf(knowledgePointUpdateRequest == null
                || knowledgePointUpdateRequest.getId() == null
                || knowledgePointUpdateRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        KnowledgePoint knowledgePoint = BeanUtil.copyProperties(knowledgePointUpdateRequest, KnowledgePoint.class);
        KnowledgePoint oldKnowledgePoint = knowledgePointService.getById(knowledgePoint.getId());
        ThrowUtils.throwIf(oldKnowledgePoint == null, ErrorCode.NOT_FOUND_ERROR);
        knowledgePointService.validKnowledgePointForUpdate(knowledgePoint, oldKnowledgePoint);
        boolean result = knowledgePointService.updateById(knowledgePoint);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }
    //TODO: 删除知识点
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteKnowledgePoint(@RequestBody DeleteRequest deleteRequest) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null || deleteRequest.getId() <= 0,
                ErrorCode.PARAMS_ERROR);
        KnowledgePoint oldKnowledgePoint = knowledgePointService.getById(deleteRequest.getId());
        ThrowUtils.throwIf(oldKnowledgePoint == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = knowledgePointService.removeKnowledgePointRecursively(deleteRequest.getId());
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }
}
