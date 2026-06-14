package com.fu.math_copilot.controller;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fu.math_copilot.annotation.AuthCheck;
import com.fu.math_copilot.common.BaseResponse;
import com.fu.math_copilot.common.DeleteRequest;
import com.fu.math_copilot.common.ErrorCode;
import com.fu.math_copilot.common.ResultUtils;
import com.fu.math_copilot.constant.UserConstant;
import com.fu.math_copilot.exception.BusinessException;
import com.fu.math_copilot.exception.ThrowUtils;
import com.fu.math_copilot.model.dto.question.QuestionAddRequest;
import com.fu.math_copilot.model.dto.question.QuestionJudgeRequest;
import com.fu.math_copilot.model.dto.question.QuestionQueryRequest;
import com.fu.math_copilot.model.dto.question.QuestionUpdateRequest;
import com.fu.math_copilot.model.entity.Question;
import com.fu.math_copilot.model.vo.QuestionJudgeVO;
import com.fu.math_copilot.model.vo.QuestionVO;
import com.fu.math_copilot.service.QuestionService;
import com.fu.math_copilot.service.UserService;
import com.fu.math_copilot.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController()
@RequestMapping("/question")
@Slf4j
@RequiredArgsConstructor
public class QuestionController {
    private final QuestionService questionService;
    private final UserService userService;

    /**
     * 根据id获取题目
     *
     * @param id
     * @return
     */
    @GetMapping("/get/vo")
    public BaseResponse<QuestionVO> getQuestionVO(Long id)
    {
        ThrowUtils.throwIf(id<=0,ErrorCode.PARAMS_ERROR);
        Question question = questionService.getQuestionFromCache(id);
        ThrowUtils.throwIf(question==null,ErrorCode.NOT_FOUND_ERROR);
        QuestionVO questionVO = questionService.getQuestionVO(question);
        questionVO.setUserVO(userService.getUserVO(userService.getById(question.getUserId())));
        return ResultUtils.success(questionVO);
    }


    /**
     * 分页获取题目列表（封装类）
     *
     * @param questionQueryRequest
     * @return
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<QuestionVO>> listQuestionVO(@RequestBody QuestionQueryRequest questionQueryRequest)
    {
           ThrowUtils.throwIf(questionQueryRequest==null,ErrorCode.PARAMS_ERROR);
        Page<Question> questionByPages = questionService.getQuestionByPages(questionQueryRequest);
        Page<QuestionVO> questionVOByPages = questionService.getQuestionVOByPages(questionByPages);
        return ResultUtils.success(questionVOByPages);
    }

    /**
     * 分页获取题目列表
     *
     * @param questionQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Question>> listQuestion(@RequestBody QuestionQueryRequest questionQueryRequest)
    {
           ThrowUtils.throwIf(questionQueryRequest==null,ErrorCode.PARAMS_ERROR);
           return ResultUtils.success(questionService.getQuestionByPages(questionQueryRequest));
    }
    /**
     * 添加题目
     *
     * @param questionAddRequest
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addQuestion(@RequestBody QuestionAddRequest questionAddRequest)
    {
        ThrowUtils.throwIf(questionAddRequest == null, ErrorCode.PARAMS_ERROR);
        // 复制
        Question question = BeanUtil.copyProperties(questionAddRequest, Question.class);
        // 校验
        questionService.validQuestion(question,true);
        // 添加当前用户 ID
        Long userId = UserContext.getCurrentUserId();
        question.setUserId(userId);
        // 保存
        boolean save = questionService.save(question);
        ThrowUtils.throwIf(!save,ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(question.getId());
    }
    /**
     * 更新题目
     *
     * @param questionUpdateRequest
     * @return
     */
    @PostMapping("/update")
    public BaseResponse<Boolean> updateQuestion(@RequestBody QuestionUpdateRequest questionUpdateRequest)
    {   // 参数校验
        if(questionUpdateRequest==null||questionUpdateRequest.getId()<=0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 复制
        Question question = BeanUtil.copyProperties(questionUpdateRequest, Question.class);
        //校验
        questionService.validQuestion(question,false);
        // 查询当前用户
        Question byId = questionService.getById(question.getId());
        ThrowUtils.throwIf(byId==null,ErrorCode.NOT_FOUND_ERROR,"题目不存在");
        boolean flag = questionService.updateById(question);
        ThrowUtils.throwIf(!flag,ErrorCode.OPERATION_ERROR);
        questionService.evictQuestionCache(question.getId());
        return ResultUtils.success(true);
    }
    /**
     * 删除题目
     *
     * @param deleteRequest
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteQuestion(@RequestBody DeleteRequest deleteRequest)
    {    // 参数校验
           if(deleteRequest==null||deleteRequest.getId()<=0)
           {
               throw new BusinessException(ErrorCode.PARAMS_ERROR);
           }
           //是否存在
        Question byId = questionService.getById(deleteRequest.getId());
        ThrowUtils.throwIf(byId==null,ErrorCode.NOT_FOUND_ERROR);
        boolean flag = questionService.removeById(byId);
        ThrowUtils.throwIf(!flag,ErrorCode.OPERATION_ERROR);
        questionService.evictQuestionCache(deleteRequest.getId());
        return ResultUtils.success(true);
    }
    /**
     * 客观题判题（单选 / 多选 / 判断 / 填空）
     */
    @PostMapping("/judge")
    public BaseResponse<QuestionJudgeVO> judgeQuestion(@RequestBody QuestionJudgeRequest judgeRequest) {
        ThrowUtils.throwIf(judgeRequest == null, ErrorCode.PARAMS_ERROR);
        QuestionJudgeVO judgeVO = questionService.judgeQuestion(judgeRequest.getQuestionId(), judgeRequest.getUserAnswer());
        return ResultUtils.success(judgeVO);
    }

    /**
     * 搜索题目
     *
     * @param questionQueryRequest
     * @return
     */
    @PostMapping("/search/page/vo")
    public BaseResponse<Page<QuestionVO>> listQuestionVOPageByEs(@RequestBody QuestionQueryRequest questionQueryRequest)
    {
        // ES 相关查询暂未开放，后续完成 ES mapping / 索引同步后再恢复
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "ES 搜索接口暂未开放");
    }
}
