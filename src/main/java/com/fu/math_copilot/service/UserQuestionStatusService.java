package com.fu.math_copilot.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fu.math_copilot.model.dto.userQuestionStatus.UserQuestionStatusQueryRequest;
import com.fu.math_copilot.model.dto.userQuestionStatus.UserQuestionStatusUpdateRequest;
import com.fu.math_copilot.model.entity.UserQuestionStatus;
import com.fu.math_copilot.model.vo.UserQuestionStatusVO;

/**
 * 针对表【user_question_status】的数据库操作 Service
 */
public interface UserQuestionStatusService extends IService<UserQuestionStatus> {

    Long updateStatus(UserQuestionStatusUpdateRequest updateRequest);

    UserQuestionStatusVO getStatusByQuestionId(Long questionId);

    Page<UserQuestionStatusVO> listMyStatusPage(UserQuestionStatusQueryRequest queryRequest);

    Page<UserQuestionStatusVO> listWrongPage(UserQuestionStatusQueryRequest queryRequest);

    Wrapper<UserQuestionStatus> getQueryWrapper(UserQuestionStatusQueryRequest queryRequest, Long userId);
}
