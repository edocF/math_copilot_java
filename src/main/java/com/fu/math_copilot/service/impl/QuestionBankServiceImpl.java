package com.fu.math_copilot.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.fu.math_copilot.common.ErrorCode;
import com.fu.math_copilot.constant.CommonConstant;
import com.fu.math_copilot.exception.BusinessException;
import com.fu.math_copilot.exception.ThrowUtils;
import com.fu.math_copilot.mapper.QuestionBankMapper;
import com.fu.math_copilot.model.dto.questionBank.QuestionBankAddRequest;
import com.fu.math_copilot.model.dto.questionBank.QuestionBankQueryRequest;
import com.fu.math_copilot.model.entity.QuestionBank;
import com.fu.math_copilot.model.entity.User;
import com.fu.math_copilot.model.vo.QuestionBankVO;
import com.fu.math_copilot.service.QuestionBankService;
import com.fu.math_copilot.service.UserService;
import com.fu.math_copilot.utils.SqlUtils;
import com.fu.math_copilot.utils.UserContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
* @author lenovo
* @description 针对表【question_bank(题库)】的数据库操作Service实现
* @createDate 2026-03-15 18:48:45
*/
@Service
public class QuestionBankServiceImpl extends ServiceImpl<QuestionBankMapper, QuestionBank> implements QuestionBankService {

    private final UserService userService;

    public QuestionBankServiceImpl(UserService userService) {
        this.userService = userService;
    }

    @Override
    public Long addQuestionBank(QuestionBankAddRequest questionBankAddRequest) {
        QuestionBank questionBank = new QuestionBank();
        BeanUtil.copyProperties(questionBankAddRequest, questionBank);
        validQuestionBank(questionBank, true);
        Long userId = UserContext.getCurrentUserId();
        questionBank.setUserId(userId);
        boolean res = save(questionBank);
        ThrowUtils.throwIf(!res, ErrorCode.OPERATION_ERROR);
        return questionBank.getId();
    }

    @Override
    public void validQuestionBank(QuestionBank questionBank, boolean add) {
        if (questionBank == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String title = questionBank.getTitle();
        String description = questionBank.getDescription();
        if (add) {   //添加时校验
            ThrowUtils.throwIf(StrUtil.isBlankIfStr(title), ErrorCode.PARAMS_ERROR, "标题不能为空");
        }
        //修改时校验
        if (StrUtil.isNotBlank(title)) {
            if (title.length() > 80) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "标题过长");
            }
        }
        if (StrUtil.isNotBlank(description)) {
            if (description.length() > 100000) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "描述过长");
            }
        }
    }

    @Override
    public Wrapper<QuestionBank> getQueryWrapper(QuestionBankQueryRequest questionBankQueryRequest) {
        if (questionBankQueryRequest == null) {
            return null;
        }
        Long id = questionBankQueryRequest.getId();
        String title = questionBankQueryRequest.getTitle();
        String searchText = questionBankQueryRequest.getSearchText();
        String sortField = questionBankQueryRequest.getSortField();
        String sortOrder = questionBankQueryRequest.getSortOrder();
        Long userId = questionBankQueryRequest.getUserId();
        String description = questionBankQueryRequest.getDescription();
        String picture = questionBankQueryRequest.getPicture();
        LambdaQueryWrapper<QuestionBank> queryWrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(searchText)) {
            queryWrapper.and(qw -> qw.like(QuestionBank::getTitle, searchText).or().like(QuestionBank::getDescription, searchText));
        }
        queryWrapper.eq(id != null, QuestionBank::getId, id)
                .like(StrUtil.isNotBlank(title), QuestionBank::getTitle, title)
                .like(StrUtil.isNotBlank(description), QuestionBank::getDescription, description)
                .eq(StrUtil.isNotBlank(picture), QuestionBank::getPicture, picture)
                .eq(userId != null, QuestionBank::getUserId, userId);
        if (SqlUtils.validSortField(sortField) && StrUtil.isNotBlank(sortOrder)) {
            boolean isAsc = CommonConstant.SORT_ORDER_ASC.equals(sortOrder);
            applySort(queryWrapper, sortField, isAsc);
        }
        return queryWrapper;
    }

    @Override
    public Page<QuestionBankVO> getQuestionBankVOPage(Page<QuestionBank> pages) {
        ThrowUtils.throwIf(pages == null, ErrorCode.PARAMS_ERROR);
        List<QuestionBank> questionBankList = pages.getRecords();
        List<QuestionBankVO> questionBankVOList = questionBankList.stream().map(questionBank -> {
            if (questionBank == null) {
                return null;
            }
            QuestionBankVO questionBankVO = new QuestionBankVO();
            BeanUtil.copyProperties(questionBank, questionBankVO);
            return questionBankVO;
        }).collect(Collectors.toList());
        Set<Long> userIdSet = questionBankList.stream().map(QuestionBank::getUserId).collect(Collectors.toSet()) ;
        Map<Long, List<User>> collect = userService.listByIds(userIdSet).stream().collect(Collectors.groupingBy(User::getId));
        questionBankVOList.forEach(questionBankVO -> {
            Long userId = questionBankVO.getUserId();
            User user = null;
            if (collect.containsKey(userId)) {
                user = collect.get(userId).get(0);
            }
            questionBankVO.setUser(userService.getUserVO(user));
        }
         );
         Page<QuestionBankVO> questionBankVOPage = new Page<>(pages.getCurrent(), pages.getSize(), pages.getTotal());
         questionBankVOPage.setRecords(questionBankVOList);
         return questionBankVOPage;
    }

    private void applySort(LambdaQueryWrapper<QuestionBank> queryWrapper, String sortField, boolean isAsc) {
        switch (sortField) {
            case "createTime":
                queryWrapper.orderBy(true, isAsc, QuestionBank::getCreateTime);
                break;
            case "updateTime":
                queryWrapper.orderBy(true, isAsc, QuestionBank::getUpdateTime);
                break;
            case "title":
                queryWrapper.orderBy(true, isAsc, QuestionBank::getTitle);
                break;
            default:
                queryWrapper.orderBy(true, isAsc, QuestionBank::getCreateTime);
                break;
        }

    }
}




