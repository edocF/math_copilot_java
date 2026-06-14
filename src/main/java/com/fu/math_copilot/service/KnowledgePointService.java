package com.fu.math_copilot.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fu.math_copilot.model.dto.knowledgePoint.KnowledgePointAddRequest;
import com.fu.math_copilot.model.dto.knowledgePoint.KnowledgePointQueryRequest;
import com.fu.math_copilot.model.entity.KnowledgePoint;
import com.fu.math_copilot.model.vo.KnowledgePointVO;

import java.util.List;

/**
 * 针对表【knowledge_point】的数据库操作 Service
 */
public interface KnowledgePointService extends IService<KnowledgePoint> {

    Long addKnowledgePoint(KnowledgePointAddRequest knowledgePointAddRequest);

    void validKnowledgePoint(KnowledgePoint knowledgePoint);

    void validKnowledgePointForUpdate(KnowledgePoint knowledgePoint, KnowledgePoint oldKnowledgePoint);

    Wrapper<KnowledgePoint> getQueryWrapper(KnowledgePointQueryRequest knowledgePointQueryRequest);

    KnowledgePointVO getKnowledgePointVOWithoutChildren(KnowledgePoint knowledgePoint);

    List<KnowledgePointVO> listKnowledgePointTree();

    boolean removeKnowledgePointRecursively(Long id);
}
