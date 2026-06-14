package com.fu.math_copilot.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fu.math_copilot.model.dto.questionKnowledgePoint.QuestionKnowledgePointBatchAddRequest;
import com.fu.math_copilot.model.dto.questionKnowledgePoint.QuestionKnowledgePointBatchRemoveRequest;
import com.fu.math_copilot.model.dto.questionKnowledgePoint.QuestionKnowledgePointListQuestionRequest;
import com.fu.math_copilot.model.dto.questionKnowledgePoint.QuestionKnowledgePointQueryRequest;
import com.fu.math_copilot.model.entity.QuestionKnowledgePoint;
import com.fu.math_copilot.model.vo.QuestionVO;

import java.util.List;
import java.util.Set;

/**
 * 针对表【question_knowledge_point】的数据库操作 Service
 */
public interface QuestionKnowledgePointService extends IService<QuestionKnowledgePoint> {

    void validate(QuestionKnowledgePoint questionKnowledgePoint, boolean add);

    Wrapper<QuestionKnowledgePoint> getQueryWrapper(QuestionKnowledgePointQueryRequest queryRequest);

    Set<Long> getKnowledgePointIdsByQuestionId(Long questionId);

    void batchAdd(QuestionKnowledgePointBatchAddRequest batchAddRequest);

    void batchRemove(QuestionKnowledgePointBatchRemoveRequest batchRemoveRequest);

    /**
     * 按知识点分页查题目（默认含子树）
     */
    Page<QuestionVO> listQuestionVOPageByKnowledgePoint(QuestionKnowledgePointListQuestionRequest request);

    void removeByQuestionId(Long questionId);

    void replaceQuestionKnowledgePoints(Long questionId, List<Long> knowledgePointIds);
}
