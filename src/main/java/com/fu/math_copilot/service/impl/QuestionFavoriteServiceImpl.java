package com.fu.math_copilot.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fu.math_copilot.common.ErrorCode;
import com.fu.math_copilot.constant.CommonConstant;
import com.fu.math_copilot.exception.BusinessException;
import com.fu.math_copilot.exception.ThrowUtils;
import com.fu.math_copilot.mapper.QuestionFavoriteMapper;
import com.fu.math_copilot.model.dto.questionFavorite.QuestionFavoriteAddRequest;
import com.fu.math_copilot.model.dto.questionFavorite.QuestionFavoriteQueryRequest;
import com.fu.math_copilot.model.dto.questionFavorite.QuestionFavoriteRemoveRequest;
import com.fu.math_copilot.model.entity.Question;
import com.fu.math_copilot.model.entity.QuestionFavorite;
import com.fu.math_copilot.model.vo.QuestionFavoriteVO;
import com.fu.math_copilot.model.vo.QuestionVO;
import com.fu.math_copilot.service.QuestionFavoriteService;
import com.fu.math_copilot.service.QuestionService;
import com.fu.math_copilot.utils.SqlUtils;
import com.fu.math_copilot.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuestionFavoriteServiceImpl extends ServiceImpl<QuestionFavoriteMapper, QuestionFavorite>
        implements QuestionFavoriteService {

    private final QuestionService questionService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addFavorite(QuestionFavoriteAddRequest addRequest) {
        ThrowUtils.throwIf(addRequest == null, ErrorCode.PARAMS_ERROR);
        Long questionId = addRequest.getQuestionId();
        validateQuestionId(questionId);
        Long userId = getLoginUserId();

        LambdaQueryWrapper<QuestionFavorite> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(QuestionFavorite::getUserId, userId)
                .eq(QuestionFavorite::getQuestionId, questionId);
        QuestionFavorite existing = this.getOne(queryWrapper);
        if (existing != null) {
            return existing.getId();
        }

        QuestionFavorite favorite = new QuestionFavorite();
        favorite.setUserId(userId);
        favorite.setQuestionId(questionId);
        try {
            boolean saved = this.save(favorite);
            ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR);
            return favorite.getId();
        } catch (DataIntegrityViolationException e) {
            QuestionFavorite duplicate = this.getOne(queryWrapper);
            ThrowUtils.throwIf(duplicate == null, ErrorCode.OPERATION_ERROR);
            return duplicate.getId();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean removeFavorite(QuestionFavoriteRemoveRequest removeRequest) {
        ThrowUtils.throwIf(removeRequest == null, ErrorCode.PARAMS_ERROR);
        Long questionId = removeRequest.getQuestionId();
        ThrowUtils.throwIf(questionId == null || questionId <= 0, ErrorCode.PARAMS_ERROR);
        Long userId = getLoginUserId();

        LambdaQueryWrapper<QuestionFavorite> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(QuestionFavorite::getUserId, userId)
                .eq(QuestionFavorite::getQuestionId, questionId);
        return this.remove(queryWrapper);
    }

    @Override
    public Page<QuestionFavoriteVO> listMyFavoritePage(QuestionFavoriteQueryRequest queryRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        Long userId = getLoginUserId();
        int current = queryRequest.getCurrent();
        int pageSize = queryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize >= 20, ErrorCode.PARAMS_ERROR, "请求过多");

        Page<QuestionFavorite> page = this.page(new Page<>(current, pageSize),
                getQueryWrapper(queryRequest, userId));
        Page<QuestionFavoriteVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<QuestionFavorite> records = page.getRecords();
        if (CollUtil.isEmpty(records)) {
            voPage.setRecords(Collections.emptyList());
            return voPage;
        }
        voPage.setRecords(records.stream()
                .map(this::toQuestionFavoriteVO)
                .collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public Boolean isFavorite(Long questionId) {
        ThrowUtils.throwIf(questionId == null || questionId <= 0, ErrorCode.PARAMS_ERROR);
        Long userId = getLoginUserId();
        LambdaQueryWrapper<QuestionFavorite> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(QuestionFavorite::getUserId, userId)
                .eq(QuestionFavorite::getQuestionId, questionId);
        return this.count(queryWrapper) > 0;
    }

    @Override
    public Wrapper<QuestionFavorite> getQueryWrapper(QuestionFavoriteQueryRequest queryRequest, Long userId) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        Long questionId = queryRequest.getQuestionId();
        String sortField = queryRequest.getSortField();
        String sortOrder = queryRequest.getSortOrder();

        LambdaQueryWrapper<QuestionFavorite> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(QuestionFavorite::getUserId, userId)
                .eq(questionId != null, QuestionFavorite::getQuestionId, questionId);
        if (SqlUtils.validSortField(sortField) && sortOrder != null) {
            boolean isAsc = CommonConstant.SORT_ORDER_ASC.equals(sortOrder);
            if ("createTime".equals(sortField)) {
                queryWrapper.orderBy(true, isAsc, QuestionFavorite::getCreateTime);
            } else {
                queryWrapper.orderByDesc(QuestionFavorite::getCreateTime);
            }
        } else {
            queryWrapper.orderByDesc(QuestionFavorite::getCreateTime);
        }
        return queryWrapper;
    }

    private QuestionFavoriteVO toQuestionFavoriteVO(QuestionFavorite favorite) {
        QuestionFavoriteVO vo = BeanUtil.copyProperties(favorite, QuestionFavoriteVO.class);
        Question question = questionService.getById(favorite.getQuestionId());
        QuestionVO questionVO = questionService.getQuestionVO(question);
        vo.setQuestionVO(questionVO);
        return vo;
    }

    private void validateQuestionId(Long questionId) {
        ThrowUtils.throwIf(questionId == null || questionId <= 0, ErrorCode.PARAMS_ERROR, "题目 id 非法");
        ThrowUtils.throwIf(questionService.getById(questionId) == null, ErrorCode.NOT_FOUND_ERROR, "题目不存在");
    }

    private Long getLoginUserId() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return userId;
    }
}
