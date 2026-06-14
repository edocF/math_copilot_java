package com.fu.math_copilot.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fu.math_copilot.common.ErrorCode;
import com.fu.math_copilot.constant.UserConstant;
import com.fu.math_copilot.exception.BusinessException;
import com.fu.math_copilot.exception.ThrowUtils;
import com.fu.math_copilot.mapper.ExamPaperMapper;
import com.fu.math_copilot.mapper.ExamPaperQuestionMapper;
import com.fu.math_copilot.model.dto.examPaper.ExamPaperGenerateRequest;
import com.fu.math_copilot.model.dto.examPaper.ExamPaperQueryRequest;
import com.fu.math_copilot.model.entity.ExamPaper;
import com.fu.math_copilot.model.entity.ExamPaperQuestion;
import com.fu.math_copilot.model.entity.Question;
import com.fu.math_copilot.model.entity.User;
import com.fu.math_copilot.model.enums.ExamGenerateTypeEnum;
import com.fu.math_copilot.model.vo.ExamPaperDetailVO;
import com.fu.math_copilot.model.vo.ExamPaperGenerateVO;
import com.fu.math_copilot.model.vo.ExamPaperQuestionItemVO;
import com.fu.math_copilot.model.vo.ExamPaperVO;
import com.fu.math_copilot.service.ExamPaperService;
import com.fu.math_copilot.service.QuestionService;
import com.fu.math_copilot.service.UserService;
import com.fu.math_copilot.utils.QuestionOptionsUtils;
import com.fu.math_copilot.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExamPaperServiceImpl extends ServiceImpl<ExamPaperMapper, ExamPaper>
        implements ExamPaperService {

    private static final int MAX_PAPER_QUESTIONS = 200;
    private static final int DEFAULT_SCORE = 5;

    private final ExamPaperQuestionMapper examPaperQuestionMapper;
    private final QuestionService questionService;
    private final UserService userService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamPaperGenerateVO generate(ExamPaperGenerateRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        validateGenerateRequest(request);
        // 检查幂等性
        if (StrUtil.isNotBlank(request.getIdempotencyKey())) {
            ExamPaper existed = this.getOne(new LambdaQueryWrapper<ExamPaper>()
                    .eq(ExamPaper::getRequestNo, request.getIdempotencyKey()));
            if (existed != null) {
                return buildGenerateVO(existed);
            }
        }
        //选取了什么题目
        List<Question> selectedQuestions = pickManualQuestions(request.getQuestionIds());
        int defaultScore = request.getDefaultScore() != null && request.getDefaultScore() > 0
                ? request.getDefaultScore() : DEFAULT_SCORE;

        ExamPaper paper = new ExamPaper();
        paper.setTitle(request.getTitle());
        paper.setUserId(getLoginUserId());
        paper.setGenerateType(ExamGenerateTypeEnum.MANUAL.getValue());
        paper.setConditionJson(JSONUtil.toJsonStr(request));
        paper.setQuestionCount(selectedQuestions.size());
        paper.setTotalScore(selectedQuestions.size() * defaultScore);
        paper.setRequestNo(StrUtil.blankToDefault(request.getIdempotencyKey(), null));
       //保存试卷
        try {
            boolean saved = this.save(paper);
            ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR);
        } catch (DuplicateKeyException e) {
            //幂等性检查
            if (StrUtil.isNotBlank(request.getIdempotencyKey())) {
                ExamPaper existed = this.getOne(new LambdaQueryWrapper<ExamPaper>()
                        .eq(ExamPaper::getRequestNo, request.getIdempotencyKey()));
                ThrowUtils.throwIf(existed == null, ErrorCode.OPERATION_ERROR);
                return buildGenerateVO(existed);
            }
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "组卷失败，请重试");
        }

        savePaperQuestions(paper.getId(), selectedQuestions, defaultScore);
        return buildGenerateVO(paper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamPaperGenerateVO regenerate(Long paperId) {
        ThrowUtils.throwIf(paperId == null || paperId <= 0, ErrorCode.PARAMS_ERROR);
        ExamPaper paper = getPaperOrThrow(paperId);
        checkPaperAccess(paper);
        ThrowUtils.throwIf(StrUtil.isBlank(paper.getConditionJson()), ErrorCode.OPERATION_ERROR, "缺少组卷条件，无法重组");
        ExamPaperGenerateRequest request = JSONUtil.toBean(paper.getConditionJson(), ExamPaperGenerateRequest.class);
        request.setIdempotencyKey(null);
        return generate(request);
    }

    @Override
    public ExamPaperDetailVO getDetail(Long paperId) {
        ExamPaper paper = getPaperOrThrow(paperId);
        checkPaperAccess(paper);
        return buildDetailVO(paper, null);
    }

    @Override
    public ExamPaperDetailVO buildDetailForExport(Long paperId, String contentScope) {
        ExamPaper paper = getPaperOrThrow(paperId);
        return buildDetailVO(paper, contentScope);
    }

    @Override
    public Page<ExamPaperVO> listMyPage(ExamPaperQueryRequest queryRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        Long userId = getLoginUserId();
        int current = queryRequest.getCurrent();
        int pageSize = queryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize >= 20, ErrorCode.PARAMS_ERROR, "请求过多");

        LambdaQueryWrapper<ExamPaper> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamPaper::getUserId, userId)
                .like(StrUtil.isNotBlank(queryRequest.getTitle()), ExamPaper::getTitle, queryRequest.getTitle())
                .orderByDesc(ExamPaper::getCreateTime);

        Page<ExamPaper> page = this.page(new Page<>(current, pageSize), wrapper);
        Page<ExamPaperVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream()
                .map(this::toExamPaperVO)
                .collect(Collectors.toList()));
        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deletePaper(Long paperId) {
        ExamPaper paper = getPaperOrThrow(paperId);
        checkPaperAccess(paper);
        examPaperQuestionMapper.delete(new LambdaQueryWrapper<ExamPaperQuestion>()
                .eq(ExamPaperQuestion::getPaperId, paperId));
        return this.removeById(paperId);
    }

    private void validateGenerateRequest(ExamPaperGenerateRequest request) {
        ThrowUtils.throwIf(StrUtil.isBlank(request.getTitle()), ErrorCode.PARAMS_ERROR, "试卷标题不能为空");
        ThrowUtils.throwIf(CollUtil.isEmpty(request.getQuestionIds()), ErrorCode.PARAMS_ERROR, "请选择题目");
        ThrowUtils.throwIf(request.getQuestionIds().size() > MAX_PAPER_QUESTIONS,
                ErrorCode.PARAMS_ERROR, "题目数量超限");
    }

    private List<Question> pickManualQuestions(List<Long> questionIds) {
        List<Long> distinctIds = questionIds.stream().distinct().collect(Collectors.toList());
        List<Question> questions = listValidQuestions(new HashSet<>(distinctIds));
        Map<Long, Question> questionMap = questions.stream()
                .collect(Collectors.toMap(Question::getId, q -> q, (a, b) -> a, LinkedHashMap::new));
        ThrowUtils.throwIf(questionMap.size() != distinctIds.size(), ErrorCode.NOT_FOUND_ERROR, "存在无效或已下架题目");
        return distinctIds.stream().map(questionMap::get).collect(Collectors.toList());
    }

    private List<Question> listValidQuestions(Set<Long> questionIds) {
        if (CollUtil.isEmpty(questionIds)) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Question::getId, questionIds);
        return questionService.list(wrapper);
    }

    private void savePaperQuestions(Long paperId, List<Question> questions, int defaultScore) {
        int sortNo = 1;
        for (Question question : questions) {
            ExamPaperQuestion item = new ExamPaperQuestion();
            item.setPaperId(paperId);
            item.setQuestionId(question.getId());
            item.setSortNo(sortNo++);
            item.setScore(defaultScore);
            item.setSectionType(question.getQuestionType());
            examPaperQuestionMapper.insert(item);
        }
    }

    private ExamPaperGenerateVO buildGenerateVO(ExamPaper paper) {
        ExamPaperGenerateVO vo = new ExamPaperGenerateVO();
        vo.setPaperId(paper.getId());
        vo.setQuestionCount(paper.getQuestionCount());
        vo.setTotalScore(paper.getTotalScore());
        vo.setDetail(buildDetailVO(paper, null));
        return vo;
    }

    private ExamPaperDetailVO buildDetailVO(ExamPaper paper, String contentScope) {
        // 获取试卷题目关联
        List<ExamPaperQuestion> relations = examPaperQuestionMapper.selectList(
                new LambdaQueryWrapper<ExamPaperQuestion>()
                        .eq(ExamPaperQuestion::getPaperId, paper.getId())
                        .orderByAsc(ExamPaperQuestion::getSortNo));
        // 获取题目ID列表
        List<Long> questionIds = relations.stream()
                .map(ExamPaperQuestion::getQuestionId)
                .collect(Collectors.toList());
        // 获取题目Map
        Map<Long, Question> questionMap = CollUtil.isEmpty(questionIds)
                ? Collections.emptyMap()
                : listValidQuestions(new HashSet<>(questionIds)).stream()
                .collect(Collectors.toMap(Question::getId, q -> q, (a, b) -> a));
        // 组装VO
        List<ExamPaperQuestionItemVO> items = new ArrayList<>();
        List<Long> missingIds = new ArrayList<>();
        for (ExamPaperQuestion relation : relations) {
            Question question = questionMap.get(relation.getQuestionId());
            if (question == null) {
                missingIds.add(relation.getQuestionId());
                continue;
            }
            ExamPaperQuestionItemVO item = new ExamPaperQuestionItemVO();
            item.setSortNo(relation.getSortNo());
            item.setQuestionId(relation.getQuestionId());
            item.setScore(relation.getScore());
            item.setQuestionType(question.getQuestionType());
            item.setDifficulty(question.getDifficulty());
            item.setContent(question.getContent());
            item.setOptions(question.getOptions());
            item.setOptionList(QuestionOptionsUtils.parseOptionList(
                    question.getId(), question.getQuestionType(), question.getOptions()));
            item.setAnswer(question.getAnswer());
            item.setAnalysis(question.getAnalysis());
            item.setPicture(question.getPicture());
            items.add(item);
        }

        ExamPaperDetailVO detail = new ExamPaperDetailVO();
        detail.setPaperId(paper.getId());
        detail.setTitle(paper.getTitle());
        detail.setGenerateType(paper.getGenerateType());
        detail.setQuestionCount(items.size());
        detail.setTotalScore(paper.getTotalScore());
        detail.setContentScope(contentScope);
        detail.setMissingQuestionIds(missingIds);
        detail.setQuestions(items);
        return detail;
    }

    private ExamPaper getPaperOrThrow(Long paperId) {
        ThrowUtils.throwIf(paperId == null || paperId <= 0, ErrorCode.PARAMS_ERROR);
        ExamPaper paper = this.getById(paperId);
        ThrowUtils.throwIf(paper == null, ErrorCode.NOT_FOUND_ERROR, "试卷不存在");
        return paper;
    }

    private void checkPaperAccess(ExamPaper paper) {
        Long userId = getLoginUserId();
        if (paper.getUserId().equals(userId)) {
            return;
        }
        User user = userService.getById(userId);
        if (user != null && UserConstant.ADMIN_ROLE.equals(user.getUserRole())) {
            return;
        }
        throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
    }

    private ExamPaperVO toExamPaperVO(ExamPaper paper) {
        return BeanUtil.copyProperties(paper, ExamPaperVO.class);
    }

    private Long getLoginUserId() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return userId;
    }
}
