package com.fu.math_copilot.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fu.math_copilot.common.ErrorCode;
import com.fu.math_copilot.constant.CommonConstant;
import com.fu.math_copilot.exception.BusinessException;
import com.fu.math_copilot.exception.ThrowUtils;
import com.fu.math_copilot.mapper.QuestionMapper;
import com.fu.math_copilot.model.dto.question.QuestionQueryRequest;
import com.fu.math_copilot.model.entity.Question;
import com.fu.math_copilot.model.entity.User;
import com.fu.math_copilot.model.enums.QuestionResultEnum;
import com.fu.math_copilot.model.enums.QuestionTypeEnum;
import com.fu.math_copilot.model.vo.QuestionJudgeVO;
import com.fu.math_copilot.model.vo.QuestionVO;
import com.fu.math_copilot.model.vo.UserVO;
import com.fu.math_copilot.service.QuestionCacheService;
import com.fu.math_copilot.service.QuestionService;
import com.fu.math_copilot.service.UserService;
import com.fu.math_copilot.utils.QuestionAnswerJudgeUtils;
import com.fu.math_copilot.utils.QuestionOptionsUtils;
import com.fu.math_copilot.utils.SqlUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
* @author lenovo
* @description 针对表【question(题目)】的数据库操作Service实现
* @createDate 2026-03-15 18:48:45
*/
@Service
@RequiredArgsConstructor
public class QuestionServiceImpl extends ServiceImpl<QuestionMapper, Question>
    implements QuestionService {
    private final UserService userService;
    private final QuestionCacheService questionCacheService;

    @Override
    public void validQuestion(Question question, boolean add) {
        ThrowUtils.throwIf(question == null, ErrorCode.PARAMS_ERROR);
        String questionType = question.getQuestionType();
        String answer = question.getAnswer();
        String content = question.getContent();
        String analysis = question.getAnalysis();
        String source = question.getSource();
        String options = question.getOptions();
        Integer difficulty = question.getDifficulty();

        ThrowUtils.throwIf(StrUtil.isBlank(questionType), ErrorCode.PARAMS_ERROR, "题型不能为空");
        ThrowUtils.throwIf(QuestionTypeEnum.getEnumByValue(questionType) == null, ErrorCode.PARAMS_ERROR, "题型非法");
        ThrowUtils.throwIf(difficulty == null || difficulty < 1 || difficulty > 5, ErrorCode.PARAMS_ERROR, "难度需在 1-5");

        if (QuestionTypeEnum.SINGLE.getValue().equals(questionType) || QuestionTypeEnum.MULTIPLE.getValue().equals(questionType)) {
            ThrowUtils.throwIf(StrUtil.isBlankIfStr(options), ErrorCode.PARAMS_ERROR, "选项不能为空");
        }
        if(add)
        {
             ThrowUtils.throwIf(StrUtil.isBlankIfStr( content), ErrorCode.PARAMS_ERROR, "内容不能为空");
             ThrowUtils.throwIf(StrUtil.isBlankIfStr( answer), ErrorCode.PARAMS_ERROR, "答案不能为空");
        }
        if (StrUtil.isNotBlank(answer)) {
            ThrowUtils.throwIf(answer.length() > 10240, ErrorCode.PARAMS_ERROR, "答案过长");
        }
        if (StrUtil.isNotBlank(content)) {
            ThrowUtils.throwIf(content.length() > 10240, ErrorCode.PARAMS_ERROR, "内容过长");
        }
        if (StrUtil.isNotBlank(analysis)) {
            ThrowUtils.throwIf(analysis.length() > 10240, ErrorCode.PARAMS_ERROR, "解析过长");
        }
        if (StrUtil.isNotBlank(source)) {
            ThrowUtils.throwIf(source.length() > 10240, ErrorCode.PARAMS_ERROR, "来源过长");
        }
    }



    @Override
    public Wrapper<Question> getQueryWrapper(QuestionQueryRequest questionQueryRequest) {
        ThrowUtils.throwIf(questionQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Long id = questionQueryRequest.getId();
        String answer = questionQueryRequest.getAnswer();
        String content = questionQueryRequest.getContent();
        String searchText = questionQueryRequest.getSearchText();
        String questionType = questionQueryRequest.getQuestionType();
        Integer difficulty = questionQueryRequest.getDifficulty();
        String source = questionQueryRequest.getSource();
        Long userId = questionQueryRequest.getUserId();
        String sortField = questionQueryRequest.getSortField();
        String sortOrder = questionQueryRequest.getSortOrder();
        QueryWrapper<Question> queryWrapper = new QueryWrapper<>();
        queryWrapper.like(StrUtil.isNotBlank(content), "content", content);
        queryWrapper.like(StrUtil.isNotBlank(answer), "answer", answer);
        queryWrapper.eq(StrUtil.isNotBlank(questionType), "questionType", questionType);
        queryWrapper.eq(difficulty != null, "difficulty", difficulty);
        queryWrapper.like(StrUtil.isNotBlank(source), "source", source);
        queryWrapper.eq(userId != null, "userId", userId);
        queryWrapper.eq(id != null, "id", id);
        if(StrUtil.isNotBlank(searchText))
        {
            queryWrapper.
                    and(qw -> qw.like("content", searchText)
                            .or().like("answer", searchText));
        }
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), CommonConstant.SORT_ORDER_ASC.equals(sortOrder), sortField);

        return queryWrapper;
    }

    @Override
    public Page<Question> getQuestionByPages(QuestionQueryRequest questionQueryRequest) {
        int current = questionQueryRequest.getCurrent();
        int pageSize = questionQueryRequest.getPageSize();
        //防爬虫
        ThrowUtils.throwIf(pageSize>=20, ErrorCode.PARAMS_ERROR,"请求过多");
        Page<Question> page = new Page<>(current, pageSize);
        return this.page(page, this.getQueryWrapper(questionQueryRequest));
    }

    @Override
    public Page<QuestionVO> getQuestionVOByPages(Page<Question> questionByPages) {
        List<Question> records = questionByPages.getRecords();
        Page<QuestionVO> questionVOPage = new Page<>(questionByPages.getCurrent(), questionByPages.getSize(), questionByPages.getTotal());
        //如果为空返回空页面
          if (CollUtil.isEmpty(records))
              return questionVOPage;
        List<QuestionVO> questionVOList = records.stream().map(this::getQuestionVO).collect(Collectors.toList());
        Set<Long> userIds = records.stream().map(Question::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userMap = userService.listByIds(userIds).stream().collect(Collectors.groupingBy(User::getId));
        if(CollUtil.isNotEmpty(questionVOList))
        {
            questionVOList.forEach(questionVO -> {
                Long userId = questionVO.getUserId();
                UserVO userVO = null;
                if(userMap.containsKey(userId))
                {
                    userVO = userService.getUserVO(userMap.get(userId).get(0));
                }
                questionVO.setUserVO(userVO);
            });
        }
        questionVOPage.setRecords(questionVOList);
        return questionVOPage;
    }

    public QuestionVO getQuestionVO(Question question) {
        if(question==null)
            return null;
        QuestionVO questionVO = new QuestionVO();
        BeanUtil.copyProperties(question, questionVO);
        questionVO.setOptionList(QuestionOptionsUtils.parseOptionList(
                question.getId(), question.getQuestionType(), question.getOptions()));
        return questionVO;
    }

    @Override
    public Page<Question> searchFromEs(QuestionQueryRequest questionQueryRequest) {
        // ES 相关能力暂时停用，待索引映射和同步链路完善后再恢复
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "ES 搜索服务暂未开放");
    }

    @Override
    public QuestionJudgeVO judgeQuestion(Long questionId, String userAnswer) {
        ThrowUtils.throwIf(questionId == null || questionId <= 0, ErrorCode.PARAMS_ERROR, "题目 id 非法");
        ThrowUtils.throwIf(StrUtil.isBlank(userAnswer), ErrorCode.PARAMS_ERROR, "用户答案不能为空");

        Question question = this.getQuestionFromCache(questionId);
        ThrowUtils.throwIf(question == null, ErrorCode.NOT_FOUND_ERROR, "题目不存在");

        String questionType = question.getQuestionType();
        ThrowUtils.throwIf(!QuestionAnswerJudgeUtils.isObjectiveType(questionType),
                ErrorCode.PARAMS_ERROR, "该题型不支持自动判题");

        boolean correct = QuestionAnswerJudgeUtils.judge(questionType, userAnswer, question.getAnswer());

        QuestionJudgeVO judgeVO = new QuestionJudgeVO();
        judgeVO.setQuestionType(questionType);
        judgeVO.setCorrect(correct);
        judgeVO.setResult(correct ? QuestionResultEnum.CORRECT.getValue() : QuestionResultEnum.WRONG.getValue());
        return judgeVO;
    }



    @Override
    public Question getQuestionFromCache(Long id) {
       return questionCacheService.getFromCache(id, this::getById);
    }



    @Override
    public void evictQuestionCache(Long id) {
      questionCacheService.evict(id);
    }

}
