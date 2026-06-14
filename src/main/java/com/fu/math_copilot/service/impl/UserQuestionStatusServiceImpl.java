package com.fu.math_copilot.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fu.math_copilot.common.ErrorCode;
import com.fu.math_copilot.constant.CommonConstant;
import com.fu.math_copilot.exception.BusinessException;
import com.fu.math_copilot.exception.ThrowUtils;
import com.fu.math_copilot.mapper.UserQuestionStatusMapper;
import com.fu.math_copilot.model.dto.userQuestionStatus.UserQuestionStatusQueryRequest;
import com.fu.math_copilot.model.dto.userQuestionStatus.UserQuestionStatusUpdateRequest;
import com.fu.math_copilot.model.entity.Question;
import com.fu.math_copilot.model.entity.UserQuestionStatus;
import com.fu.math_copilot.model.enums.QuestionResultEnum;
import com.fu.math_copilot.model.enums.QuestionStatusEnum;
import com.fu.math_copilot.model.vo.QuestionVO;
import com.fu.math_copilot.model.vo.UserQuestionStatusVO;
import com.fu.math_copilot.service.QuestionService;
import com.fu.math_copilot.service.UserQuestionStatusService;
import com.fu.math_copilot.utils.SqlUtils;
import com.fu.math_copilot.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserQuestionStatusServiceImpl extends ServiceImpl<UserQuestionStatusMapper, UserQuestionStatus>
        implements UserQuestionStatusService {

    private final QuestionService questionService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long updateStatus(UserQuestionStatusUpdateRequest updateRequest) {
        ThrowUtils.throwIf(updateRequest == null, ErrorCode.PARAMS_ERROR);
        Long userId = getLoginUserId();
        Long questionId = updateRequest.getQuestionId();
        String status = updateRequest.getStatus();
        String result = updateRequest.getResult();
        validateStatusUpdate(questionId, status, result);

        LambdaQueryWrapper<UserQuestionStatus> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserQuestionStatus::getUserId, userId)
                .eq(UserQuestionStatus::getQuestionId, questionId);
        UserQuestionStatus existing = this.getOne(queryWrapper);

        String finalResult = QuestionStatusEnum.DONE.getValue().equals(status) ? result : null;
        if (existing == null) {
            UserQuestionStatus record = new UserQuestionStatus();
            record.setUserId(userId);
            record.setQuestionId(questionId);
            record.setStatus(status);
            record.setResult(finalResult);
            boolean saved = this.save(record);
            ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR);
            return record.getId();
        }
        existing.setStatus(status);
        existing.setResult(finalResult);
        boolean updated = this.updateById(existing);
        ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR);
        return existing.getId();
    }

    @Override
    public UserQuestionStatusVO getStatusByQuestionId(Long questionId) {
        ThrowUtils.throwIf(questionId == null || questionId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(questionService.getById(questionId) == null, ErrorCode.NOT_FOUND_ERROR, "题目不存在");
        Long userId = getLoginUserId();

        LambdaQueryWrapper<UserQuestionStatus> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserQuestionStatus::getUserId, userId)
                .eq(UserQuestionStatus::getQuestionId, questionId);
        UserQuestionStatus record = this.getOne(queryWrapper);
        if (record == null) {
            UserQuestionStatusVO vo = new UserQuestionStatusVO();
            vo.setQuestionId(questionId);
            vo.setStatus(QuestionStatusEnum.NOT_DONE.getValue());
            vo.setResult(null);
            return vo;
        }
        return toUserQuestionStatusVO(record, false);
    }

    @Override
    public Page<UserQuestionStatusVO> listMyStatusPage(UserQuestionStatusQueryRequest queryRequest) {
        return listStatusPage(queryRequest, null, null);
    }

    @Override
    public Page<UserQuestionStatusVO> listWrongPage(UserQuestionStatusQueryRequest queryRequest) {
        return listStatusPage(queryRequest, QuestionStatusEnum.DONE.getValue(), QuestionResultEnum.WRONG.getValue());
    }

    @Override
    public Wrapper<UserQuestionStatus> getQueryWrapper(UserQuestionStatusQueryRequest queryRequest, Long userId) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        Long questionId = queryRequest.getQuestionId();
        String status = queryRequest.getStatus();
        String result = queryRequest.getResult();
        String sortField = queryRequest.getSortField();
        String sortOrder = queryRequest.getSortOrder();

        LambdaQueryWrapper<UserQuestionStatus> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserQuestionStatus::getUserId, userId)
                .eq(questionId != null, UserQuestionStatus::getQuestionId, questionId)
                .eq(StrUtil.isNotBlank(status), UserQuestionStatus::getStatus, status)
                .eq(StrUtil.isNotBlank(result), UserQuestionStatus::getResult, result);
        if (SqlUtils.validSortField(sortField) && StrUtil.isNotBlank(sortOrder)) {
            boolean isAsc = CommonConstant.SORT_ORDER_ASC.equals(sortOrder);
            applySort(queryWrapper, sortField, isAsc);
        } else {
            queryWrapper.orderByDesc(UserQuestionStatus::getUpdateTime);
        }
        return queryWrapper;
    }

    private Page<UserQuestionStatusVO> listStatusPage(UserQuestionStatusQueryRequest queryRequest,
                                                      String fixedStatus,
                                                      String fixedResult) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        Long userId = getLoginUserId();
        int current = queryRequest.getCurrent();
        int pageSize = queryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize >= 20, ErrorCode.PARAMS_ERROR, "请求过多");

        if (StrUtil.isNotBlank(fixedStatus)) {
            queryRequest.setStatus(fixedStatus);
        }
        if (StrUtil.isNotBlank(fixedResult)) {
            queryRequest.setResult(fixedResult);
        }
        if (StrUtil.isNotBlank(queryRequest.getStatus())) {
            ThrowUtils.throwIf(QuestionStatusEnum.getEnumByValue(queryRequest.getStatus()) == null,
                    ErrorCode.PARAMS_ERROR, "状态非法");
        }
        if (StrUtil.isNotBlank(queryRequest.getResult())) {
            ThrowUtils.throwIf(QuestionResultEnum.getEnumByValue(queryRequest.getResult()) == null,
                    ErrorCode.PARAMS_ERROR, "结果非法");
        }

        Page<UserQuestionStatus> page = this.page(new Page<>(current, pageSize),
                getQueryWrapper(queryRequest, userId));
        Page<UserQuestionStatusVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<UserQuestionStatus> records = page.getRecords();
        if (CollUtil.isEmpty(records)) {
            voPage.setRecords(Collections.emptyList());
            return voPage;
        }
        voPage.setRecords(records.stream()
                .map(record -> toUserQuestionStatusVO(record, true))
                .collect(Collectors.toList()));
        return voPage;
    }

    private void validateStatusUpdate(Long questionId, String status, String result) {
        ThrowUtils.throwIf(questionId == null || questionId <= 0, ErrorCode.PARAMS_ERROR, "题目 id 非法");
        ThrowUtils.throwIf(StrUtil.isBlank(status), ErrorCode.PARAMS_ERROR, "状态不能为空");
        ThrowUtils.throwIf(QuestionStatusEnum.getEnumByValue(status) == null, ErrorCode.PARAMS_ERROR, "状态非法");
        ThrowUtils.throwIf(questionService.getById(questionId) == null, ErrorCode.NOT_FOUND_ERROR, "题目不存在");

        if (QuestionStatusEnum.DONE.getValue().equals(status)) {
            ThrowUtils.throwIf(StrUtil.isBlank(result), ErrorCode.PARAMS_ERROR, "完成状态必须填写结果");
            ThrowUtils.throwIf(QuestionResultEnum.getEnumByValue(result) == null, ErrorCode.PARAMS_ERROR, "结果非法");
        } else {
            ThrowUtils.throwIf(StrUtil.isNotBlank(result), ErrorCode.PARAMS_ERROR, "非完成状态不能填写结果");
        }
    }

    private UserQuestionStatusVO toUserQuestionStatusVO(UserQuestionStatus record, boolean withQuestion) {
        UserQuestionStatusVO vo = BeanUtil.copyProperties(record, UserQuestionStatusVO.class);
        if (withQuestion) {
            Question question = questionService.getById(record.getQuestionId());
            QuestionVO questionVO = questionService.getQuestionVO(question);
            vo.setQuestionVO(questionVO);
        }
        return vo;
    }

    private void applySort(LambdaQueryWrapper<UserQuestionStatus> queryWrapper, String sortField, boolean isAsc) {
        switch (sortField) {
            case "createTime":
                queryWrapper.orderBy(true, isAsc, UserQuestionStatus::getCreateTime);
                break;
            case "updateTime":
                queryWrapper.orderBy(true, isAsc, UserQuestionStatus::getUpdateTime);
                break;
            default:
                queryWrapper.orderByDesc(UserQuestionStatus::getUpdateTime);
                break;
        }
    }

    private Long getLoginUserId() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return userId;
    }
}
