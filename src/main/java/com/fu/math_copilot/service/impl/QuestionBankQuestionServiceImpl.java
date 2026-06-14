package com.fu.math_copilot.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.fu.math_copilot.common.ErrorCode;
import com.fu.math_copilot.constant.CommonConstant;
import com.fu.math_copilot.exception.BusinessException;
import com.fu.math_copilot.exception.ThrowUtils;
import com.fu.math_copilot.mapper.QuestionBankQuestionMapper;
import com.fu.math_copilot.model.dto.questionBankQuestion.QuestionBankQuestionBatchAddRequest;
import com.fu.math_copilot.model.dto.questionBankQuestion.QuestionBankQuestionBatchRemoveRequest;
import com.fu.math_copilot.model.dto.questionBankQuestion.QuestionBankQuestionQueryRequest;
import com.fu.math_copilot.model.entity.Question;
import com.fu.math_copilot.model.entity.QuestionBank;
import com.fu.math_copilot.model.entity.QuestionBankQuestion;
import com.fu.math_copilot.service.QuestionBankQuestionService;
import com.fu.math_copilot.service.QuestionBankService;
import com.fu.math_copilot.service.QuestionService;
import com.fu.math_copilot.config.AsyncConfig;
import com.fu.math_copilot.utils.SqlUtils;
import com.fu.math_copilot.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
* @author lenovo
* @description 针对表【question_bank_question(题库题目)】的数据库操作Service实现
* @createDate 2026-03-15 18:48:45
*/
@Service
@Slf4j
public class QuestionBankQuestionServiceImpl extends ServiceImpl<QuestionBankQuestionMapper, QuestionBankQuestion>
    implements QuestionBankQuestionService {
    private final QuestionService questionService;
    private final QuestionBankService questionBankService;
    private final Executor questionBankQuestionBatchAddExecutor;

    public QuestionBankQuestionServiceImpl(
            QuestionService questionService,
            QuestionBankService questionBankService,
            @Qualifier(AsyncConfig.QUESTION_BANK_QUESTION_BATCH_ADD_EXECUTOR) Executor questionBankQuestionBatchAddExecutor) {
        this.questionService = questionService;
        this.questionBankService = questionBankService;
        this.questionBankQuestionBatchAddExecutor = questionBankQuestionBatchAddExecutor;
    }

    @Override
    public Set<Long> getQuestionIdsByQuestionBankId(Long id) {
        LambdaQueryWrapper<QuestionBankQuestion> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper
                .eq(QuestionBankQuestion::getQuestionBankId, id)
                .select(QuestionBankQuestion::getQuestionId);
        Set<Long> questionIdSet = this.list(queryWrapper).stream().map(QuestionBankQuestion::getQuestionId).collect(Collectors.toSet());
        return questionIdSet;
    }

    @Override
    public void validate(QuestionBankQuestion questionBankQuestion, boolean add) {
        ThrowUtils.throwIf(questionBankQuestion == null, ErrorCode.PARAMS_ERROR);
        if (questionBankQuestion.getId() != null && questionBankQuestion.getId() <= 0) {
            throw new RuntimeException("id不能小于0");
        }
        if (questionBankQuestion.getQuestionId() != null && questionBankQuestion.getQuestionId() <= 0) {
            throw new RuntimeException("questionId不能小于0");
        }
        if (questionBankQuestion.getQuestionBankId() != null && questionBankQuestion.getQuestionBankId() <= 0) {
            throw new RuntimeException("questionBankId不能小于0");
        }
        Question question = questionService.getById(questionBankQuestion.getQuestionId());
        QuestionBank questionBank = questionBankService.getById(questionBankQuestion.getQuestionBankId());
        ThrowUtils.throwIf(question == null, ErrorCode.NOT_FOUND_ERROR);
        ThrowUtils.throwIf(questionBank == null, ErrorCode.NOT_FOUND_ERROR);
        return;
    }

    @Override
    public Wrapper<QuestionBankQuestion> getQueryWrapper(QuestionBankQuestionQueryRequest questionBankQuestionQueryRequest) {
        ThrowUtils.throwIf(questionBankQuestionQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Long id = questionBankQuestionQueryRequest.getId();
        Long questionId = questionBankQuestionQueryRequest.getQuestionId();
        Long questionBankId = questionBankQuestionQueryRequest.getQuestionBankId();
        Long userId = questionBankQuestionQueryRequest.getUserId();
        Date createTime = questionBankQuestionQueryRequest.getCreateTime();
        Date updateTime = questionBankQuestionQueryRequest.getUpdateTime();
        String sortField = questionBankQuestionQueryRequest.getSortField();
        String sortOrder = questionBankQuestionQueryRequest.getSortOrder();

        QueryWrapper<QuestionBankQuestion> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(id != null, "id", id);
        queryWrapper.eq(questionId != null, "questionId", questionId);
        queryWrapper.eq(questionBankId != null, "questionBankId", questionBankId);
        queryWrapper.eq(userId != null, "userId", userId);
        queryWrapper.eq(createTime != null, "createTime", createTime);
        queryWrapper.eq(updateTime != null, "updateTime", updateTime);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC), sortField);
        return queryWrapper;
    }

    @Override
    public void QuestionBatchAddToQuestionBank(QuestionBankQuestionBatchAddRequest questionBankQuestionBatchAddRequest) {
        //校验输入参数
           ThrowUtils.throwIf(questionBankQuestionBatchAddRequest == null, ErrorCode.PARAMS_ERROR);
           Long questionBankId = questionBankQuestionBatchAddRequest.getQuestionBankId();
           List<Long> questionIdList = questionBankQuestionBatchAddRequest.getQuestionIdList();
        //校验题库是否存在
        QuestionBank questionBank = questionBankService.getById(questionBankId);
        ThrowUtils.throwIf(questionBank == null, ErrorCode.NOT_FOUND_ERROR,"题库不存在");
        //校验题目是否存在
        LambdaQueryWrapper<Question> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper
                .select(Question::getId)
                .in(Question::getId, questionIdList);
        List<Long> validQuestionIds = questionService.listObjs(queryWrapper, obj -> (Long) obj);
        ThrowUtils.throwIf(validQuestionIds==null, ErrorCode.NOT_FOUND_ERROR,"有效题目不存在");
        //校验题目是否已经加入题库中
        LambdaQueryWrapper<QuestionBankQuestion> queryWrapper1 = new LambdaQueryWrapper<>();
        queryWrapper1
                .select(QuestionBankQuestion::getQuestionId)
                .eq(QuestionBankQuestion::getQuestionBankId, questionBankId)
                .in(QuestionBankQuestion::getQuestionId, validQuestionIds);
        List<Long> alreadyExistedQuestionIds = this.listObjs(queryWrapper1, obj -> (Long) obj);
        List<Long> insertQuestionIds = validQuestionIds.stream().filter(questionId -> !alreadyExistedQuestionIds.contains(questionId)).collect(Collectors.toList());
        ThrowUtils.throwIf(CollUtil.isEmpty(insertQuestionIds), ErrorCode.OPERATION_ERROR,"所有题目已经加入题库");

        // 保存所有批次任务
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        //将题目分批加入题库
        int batchSize = 1;
        for (int i = 0; i < insertQuestionIds.size(); i += batchSize) {
            List<Long> batchQuestionIds = insertQuestionIds.subList(i, Math.min(i + batchSize, insertQuestionIds.size()));
            List<QuestionBankQuestion> questionBankQuestionList = batchQuestionIds.stream().map(questionId -> {
                QuestionBankQuestion questionBankQuestion = new QuestionBankQuestion();
                questionBankQuestion.setQuestionBankId(questionBankId);
                questionBankQuestion.setQuestionId(questionId);
                questionBankQuestion.setUserId(UserContext.getCurrentUserId());
                return questionBankQuestion;
            }).collect(Collectors.toList());
            //获取当前代理
            QuestionBankQuestionService questionBankQuestionService = (QuestionBankQuestionServiceImpl) AopContext.currentProxy();
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                questionBankQuestionService.batchAddQuestionsToBankInner(questionBankQuestionList);
            }, questionBankQuestionBatchAddExecutor);
            futures.add(future);
        }
        // 等待所有批次完成操作
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return;
    }

    /**
     * 批量添加题目到题库（事务，仅供内部调用）
     *
     * @param questionBankQuestions
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void batchAddQuestionsToBankInner(List<QuestionBankQuestion> questionBankQuestions) {
        try {
            boolean result = this.saveBatch(questionBankQuestions);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "向题库添加题目失败");
        } catch (DataIntegrityViolationException e) {
            log.error("数据库唯一键冲突或违反其他完整性约束, 错误信息: {}", e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "题目已存在于该题库，无法重复添加");
        } catch (DataAccessException e) {
            log.error("数据库连接问题、事务问题等导致操作失败, 错误信息: {}", e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "数据库操作失败");
        } catch (Exception e) {
            // 捕获其他异常，做通用处理
            log.error("添加题目到题库时发生未知错误，错误信息: {}", e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "向题库添加题目失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void QuestionBatchRemoveFromQuestionBank(QuestionBankQuestionBatchRemoveRequest questionBankQuestionBatchRemoveRequest) {
        ThrowUtils.throwIf(questionBankQuestionBatchRemoveRequest == null, ErrorCode.PARAMS_ERROR);
        Long questionBankId = questionBankQuestionBatchRemoveRequest.getQuestionBankId();
        ThrowUtils.throwIf(questionBankId == null, ErrorCode.PARAMS_ERROR);
        QuestionBank questionBank = questionBankService.getById(questionBankId);
        ThrowUtils.throwIf(questionBank == null, ErrorCode.NOT_FOUND_ERROR, "题库不存在");
        List<Long> questionIdList = questionBankQuestionBatchRemoveRequest.getQuestionIdList();
        ThrowUtils.throwIf(CollUtil.isEmpty(questionIdList), ErrorCode.PARAMS_ERROR, "请选择要删除的题目");
        LambdaQueryWrapper<QuestionBankQuestion> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(QuestionBankQuestion::getQuestionBankId, questionBankId)
                .in(QuestionBankQuestion::getQuestionId, questionIdList);
        this.remove(queryWrapper);
    }
}




