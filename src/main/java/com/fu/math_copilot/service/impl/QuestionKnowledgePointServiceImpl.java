package com.fu.math_copilot.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fu.math_copilot.common.ErrorCode;
import com.fu.math_copilot.constant.CommonConstant;
import com.fu.math_copilot.exception.BusinessException;
import com.fu.math_copilot.exception.ThrowUtils;
import com.fu.math_copilot.mapper.QuestionKnowledgePointMapper;
import com.fu.math_copilot.model.dto.questionKnowledgePoint.QuestionKnowledgePointBatchAddRequest;
import com.fu.math_copilot.model.dto.questionKnowledgePoint.QuestionKnowledgePointBatchRemoveRequest;
import com.fu.math_copilot.model.dto.questionKnowledgePoint.QuestionKnowledgePointListQuestionRequest;
import com.fu.math_copilot.model.dto.questionKnowledgePoint.QuestionKnowledgePointQueryRequest;
import com.fu.math_copilot.model.entity.KnowledgePoint;
import com.fu.math_copilot.model.entity.Question;
import com.fu.math_copilot.model.entity.QuestionKnowledgePoint;
import com.fu.math_copilot.model.vo.QuestionVO;
import com.fu.math_copilot.service.KnowledgePointService;
import com.fu.math_copilot.service.QuestionKnowledgePointService;
import com.fu.math_copilot.service.QuestionService;
import com.fu.math_copilot.utils.SqlUtils;
import com.fu.math_copilot.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuestionKnowledgePointServiceImpl extends ServiceImpl<QuestionKnowledgePointMapper, QuestionKnowledgePoint>
        implements QuestionKnowledgePointService {

    private final QuestionService questionService;
    private final KnowledgePointService knowledgePointService;

    @Override
    public void validate(QuestionKnowledgePoint questionKnowledgePoint, boolean add) {
        ThrowUtils.throwIf(questionKnowledgePoint == null, ErrorCode.PARAMS_ERROR);
        Long questionId = questionKnowledgePoint.getQuestionId();
        Long knowledgePointId = questionKnowledgePoint.getKnowledgePointId();
        ThrowUtils.throwIf(questionId == null || questionId <= 0, ErrorCode.PARAMS_ERROR, "题目 id 非法");
        ThrowUtils.throwIf(knowledgePointId == null || knowledgePointId <= 0, ErrorCode.PARAMS_ERROR, "知识点 id 非法");
        ThrowUtils.throwIf(questionService.getById(questionId) == null, ErrorCode.NOT_FOUND_ERROR, "题目不存在");
        ThrowUtils.throwIf(knowledgePointService.getById(knowledgePointId) == null, ErrorCode.NOT_FOUND_ERROR, "知识点不存在");
    }

    @Override
    public Wrapper<QuestionKnowledgePoint> getQueryWrapper(QuestionKnowledgePointQueryRequest queryRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        Long id = queryRequest.getId();
        Long questionId = queryRequest.getQuestionId();
        Long knowledgePointId = queryRequest.getKnowledgePointId();
        Long userId = queryRequest.getUserId();
        String sortField = queryRequest.getSortField();
        String sortOrder = queryRequest.getSortOrder();

        QueryWrapper<QuestionKnowledgePoint> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(id != null, "id", id);
        queryWrapper.eq(questionId != null, "questionId", questionId);
        queryWrapper.eq(knowledgePointId != null, "knowledgePointId", knowledgePointId);
        queryWrapper.eq(userId != null, "userId", userId);
        if (SqlUtils.validSortField(sortField) && StrUtil.isNotBlank(sortOrder)) {
            queryWrapper.orderBy(true, CommonConstant.SORT_ORDER_ASC.equals(sortOrder), sortField);
        } else {
            queryWrapper.orderByDesc("createTime");
        }
        return queryWrapper;
    }

    @Override
    public Set<Long> getKnowledgePointIdsByQuestionId(Long questionId) {
        if (questionId == null || questionId <= 0) {
            return Collections.emptySet();
        }
        LambdaQueryWrapper<QuestionKnowledgePoint> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(QuestionKnowledgePoint::getQuestionId, questionId)
                .select(QuestionKnowledgePoint::getKnowledgePointId);
        return this.list(queryWrapper).stream()
                .map(QuestionKnowledgePoint::getKnowledgePointId)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchAdd(QuestionKnowledgePointBatchAddRequest batchAddRequest) {
        ThrowUtils.throwIf(batchAddRequest == null, ErrorCode.PARAMS_ERROR);
        Long knowledgePointId = batchAddRequest.getKnowledgePointId();
        List<Long> questionIdList = batchAddRequest.getQuestionIdList();
        ThrowUtils.throwIf(knowledgePointId == null || knowledgePointId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(CollUtil.isEmpty(questionIdList), ErrorCode.PARAMS_ERROR, "题目列表不能为空");
        ThrowUtils.throwIf(knowledgePointService.getById(knowledgePointId) == null, ErrorCode.NOT_FOUND_ERROR, "知识点不存在");

        List<Long> validQuestionIds = questionService.listObjs(
                new LambdaQueryWrapper<Question>().select(Question::getId).in(Question::getId, questionIdList),
                obj -> (Long) obj);
        ThrowUtils.throwIf(CollUtil.isEmpty(validQuestionIds), ErrorCode.NOT_FOUND_ERROR, "有效题目不存在");

        Set<Long> existingQuestionIds = this.listObjs(
                new LambdaQueryWrapper<QuestionKnowledgePoint>()
                        .select(QuestionKnowledgePoint::getQuestionId)
                        .eq(QuestionKnowledgePoint::getKnowledgePointId, knowledgePointId)
                        .in(QuestionKnowledgePoint::getQuestionId, validQuestionIds),
                obj -> (Long) obj).stream().collect(Collectors.toSet());

        List<Long> insertQuestionIds = validQuestionIds.stream()
                .filter(id -> !existingQuestionIds.contains(id))
                .collect(Collectors.toList());
        ThrowUtils.throwIf(CollUtil.isEmpty(insertQuestionIds), ErrorCode.OPERATION_ERROR, "所有题目已绑定该知识点");

        Long userId = UserContext.getCurrentUserId();
        List<QuestionKnowledgePoint> toSave = insertQuestionIds.stream().map(questionId -> {
            QuestionKnowledgePoint relation = new QuestionKnowledgePoint();
            relation.setQuestionId(questionId);
            relation.setKnowledgePointId(knowledgePointId);
            relation.setUserId(userId);
            return relation;
        }).collect(Collectors.toList());
        try {
            boolean result = this.saveBatch(toSave);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "题目已绑定该知识点，无法重复添加");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchRemove(QuestionKnowledgePointBatchRemoveRequest batchRemoveRequest) {
        ThrowUtils.throwIf(batchRemoveRequest == null, ErrorCode.PARAMS_ERROR);
        Long knowledgePointId = batchRemoveRequest.getKnowledgePointId();
        List<Long> questionIdList = batchRemoveRequest.getQuestionIdList();
        ThrowUtils.throwIf(knowledgePointId == null || knowledgePointId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(CollUtil.isEmpty(questionIdList), ErrorCode.PARAMS_ERROR, "题目列表不能为空");
        ThrowUtils.throwIf(knowledgePointService.getById(knowledgePointId) == null, ErrorCode.NOT_FOUND_ERROR, "知识点不存在");

        this.remove(new LambdaQueryWrapper<QuestionKnowledgePoint>()
                .eq(QuestionKnowledgePoint::getKnowledgePointId, knowledgePointId)
                .in(QuestionKnowledgePoint::getQuestionId, questionIdList));
    }

    @Override
    public Page<QuestionVO> listQuestionVOPageByKnowledgePoint(QuestionKnowledgePointListQuestionRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        Long knowledgePointId = request.getKnowledgePointId();
        ThrowUtils.throwIf(knowledgePointId == null || knowledgePointId <= 0, ErrorCode.PARAMS_ERROR);

        KnowledgePoint knowledgePoint = knowledgePointService.getById(knowledgePointId);
        ThrowUtils.throwIf(knowledgePoint == null, ErrorCode.NOT_FOUND_ERROR, "知识点不存在");

        boolean includeChildren = request.getIncludeChildren() == null || request.getIncludeChildren();
        Set<Long> knowledgePointIds = resolveKnowledgePointIds(knowledgePoint, includeChildren);
        Set<Long> questionIds = getQuestionIdsByKnowledgePointIds(knowledgePointIds);

        int current = request.getCurrent();
        int pageSize = request.getPageSize();
        ThrowUtils.throwIf(pageSize >= 20, ErrorCode.PARAMS_ERROR, "请求过多");

        Page<Question> questionPage = new Page<>(current, pageSize);
        if (CollUtil.isEmpty(questionIds)) {
            return questionService.getQuestionVOByPages(questionPage);
        }

        QueryWrapper<Question> questionQueryWrapper = new QueryWrapper<Question>()
                .in("id", questionIds);
        if (StrUtil.isNotBlank(request.getQuestionType())) {
            questionQueryWrapper.eq("questionType", request.getQuestionType());
        }
        if (request.getDifficulty() != null) {
            questionQueryWrapper.eq("difficulty", request.getDifficulty());
        }
        questionQueryWrapper.orderByDesc("createTime");
        questionPage = questionService.page(questionPage, questionQueryWrapper);
        return questionService.getQuestionVOByPages(questionPage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeByQuestionId(Long questionId) {
        if (questionId == null || questionId <= 0) {
            return;
        }
        this.remove(new LambdaQueryWrapper<QuestionKnowledgePoint>()
                .eq(QuestionKnowledgePoint::getQuestionId, questionId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceQuestionKnowledgePoints(Long questionId, List<Long> knowledgePointIds) {
        ThrowUtils.throwIf(questionId == null || questionId <= 0, ErrorCode.PARAMS_ERROR);
        removeByQuestionId(questionId);
        if (CollUtil.isEmpty(knowledgePointIds)) {
            return;
        }
        List<Long> distinctIds = knowledgePointIds.stream().distinct().collect(Collectors.toList());
        long validCount = knowledgePointService.count(new LambdaQueryWrapper<KnowledgePoint>()
                .in(KnowledgePoint::getId, distinctIds));
        ThrowUtils.throwIf(validCount != distinctIds.size(), ErrorCode.NOT_FOUND_ERROR, "存在无效知识点");

        Long userId = UserContext.getCurrentUserId();
        List<QuestionKnowledgePoint> relations = distinctIds.stream().map(kpId -> {
            QuestionKnowledgePoint relation = new QuestionKnowledgePoint();
            relation.setQuestionId(questionId);
            relation.setKnowledgePointId(kpId);
            relation.setUserId(userId);
            return relation;
        }).collect(Collectors.toList());
        this.saveBatch(relations);
    }

    private Set<Long> resolveKnowledgePointIds(KnowledgePoint knowledgePoint, boolean includeChildren) {
        if (!includeChildren) {
            return Collections.singleton(knowledgePoint.getId());
        }
        String path = knowledgePoint.getPath();
        ThrowUtils.throwIf(StrUtil.isBlank(path), ErrorCode.PARAMS_ERROR, "知识点 path 数据异常");
        List<KnowledgePoint> subtree = knowledgePointService.list(
                new LambdaQueryWrapper<KnowledgePoint>().likeRight(KnowledgePoint::getPath, path));
        return subtree.stream().map(KnowledgePoint::getId).collect(Collectors.toSet());
    }

    private Set<Long> getQuestionIdsByKnowledgePointIds(Set<Long> knowledgePointIds) {
        if (CollUtil.isEmpty(knowledgePointIds)) {
            return new HashSet<>();
        }
        LambdaQueryWrapper<QuestionKnowledgePoint> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(QuestionKnowledgePoint::getKnowledgePointId, knowledgePointIds)
                .select(QuestionKnowledgePoint::getQuestionId);
        return this.list(queryWrapper).stream()
                .map(QuestionKnowledgePoint::getQuestionId)
                .collect(Collectors.toSet());
    }
}
