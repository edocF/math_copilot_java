package com.fu.math_copilot.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fu.math_copilot.model.dto.questionFavorite.QuestionFavoriteAddRequest;
import com.fu.math_copilot.model.dto.questionFavorite.QuestionFavoriteQueryRequest;
import com.fu.math_copilot.model.dto.questionFavorite.QuestionFavoriteRemoveRequest;
import com.fu.math_copilot.model.entity.QuestionFavorite;
import com.fu.math_copilot.model.vo.QuestionFavoriteVO;

/**
 * 针对表【question_favorite】的数据库操作 Service
 */
public interface QuestionFavoriteService extends IService<QuestionFavorite> {

    Long addFavorite(QuestionFavoriteAddRequest addRequest);

    Boolean removeFavorite(QuestionFavoriteRemoveRequest removeRequest);

    Page<QuestionFavoriteVO> listMyFavoritePage(QuestionFavoriteQueryRequest queryRequest);

    Boolean isFavorite(Long questionId);

    Wrapper<QuestionFavorite> getQueryWrapper(QuestionFavoriteQueryRequest queryRequest, Long userId);
}
