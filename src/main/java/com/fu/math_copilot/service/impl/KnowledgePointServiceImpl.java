package com.fu.math_copilot.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fu.math_copilot.common.ErrorCode;
import com.fu.math_copilot.constant.CommonConstant;
import com.fu.math_copilot.exception.BusinessException;
import com.fu.math_copilot.exception.ThrowUtils;
import com.fu.math_copilot.mapper.KnowledgePointMapper;
import com.fu.math_copilot.model.dto.knowledgePoint.KnowledgePointAddRequest;
import com.fu.math_copilot.model.dto.knowledgePoint.KnowledgePointQueryRequest;
import com.fu.math_copilot.model.entity.KnowledgePoint;
import com.fu.math_copilot.model.vo.KnowledgePointVO;
import com.fu.math_copilot.service.KnowledgePointService;
import com.fu.math_copilot.utils.SqlUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class KnowledgePointServiceImpl extends ServiceImpl<KnowledgePointMapper, KnowledgePoint>
        implements KnowledgePointService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addKnowledgePoint(KnowledgePointAddRequest knowledgePointAddRequest) {
        ThrowUtils.throwIf(knowledgePointAddRequest == null, ErrorCode.PARAMS_ERROR);
        KnowledgePoint knowledgePoint = BeanUtil.copyProperties(knowledgePointAddRequest, KnowledgePoint.class);
        validKnowledgePoint(knowledgePoint);
        if (knowledgePoint.getParentId() == null) {
            knowledgePoint.setParentId(0L);
        }
        if (knowledgePoint.getSort() == null) {
            knowledgePoint.setSort(0);
        }

        Long parentId = knowledgePoint.getParentId();
        String parentPath;
        int level;
        if (parentId == 0L) {
            parentPath = "/";
            level = 1;
        } else {
            KnowledgePoint parentKnowledgePoint = this.getById(parentId);
            ThrowUtils.throwIf(parentKnowledgePoint == null, ErrorCode.NOT_FOUND_ERROR, "父节点不存在");
            parentPath = parentKnowledgePoint.getPath();
            level = parentKnowledgePoint.getLevel() + 1;
        }

        // 自增 id 需先入库，再回写 path
        knowledgePoint.setLevel(level);
        knowledgePoint.setPath("/");
        boolean result = this.save(knowledgePoint);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

        String path = parentPath + knowledgePoint.getId() + "/";
        knowledgePoint.setPath(path);
        boolean updated = this.updateById(knowledgePoint);
        ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR);
        return knowledgePoint.getId();
    }

    @Override
    public void validKnowledgePoint(KnowledgePoint knowledgePoint) {
        if (knowledgePoint == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String name = knowledgePoint.getName();
        ThrowUtils.throwIf(StrUtil.isBlank(name), ErrorCode.PARAMS_ERROR, "知识点名称不能为空");
        if (StrUtil.isNotBlank(name)) {
            ThrowUtils.throwIf(name.length() > 128, ErrorCode.PARAMS_ERROR, "知识点名称过长");
        }
        Integer sort = knowledgePoint.getSort();
        if (sort != null) {
            ThrowUtils.throwIf(sort < 0, ErrorCode.PARAMS_ERROR, "排序值不能小于 0");
        }
    }

    @Override
    public void validKnowledgePointForUpdate(KnowledgePoint knowledgePoint, KnowledgePoint oldKnowledgePoint) {
        ThrowUtils.throwIf(knowledgePoint == null || oldKnowledgePoint == null, ErrorCode.PARAMS_ERROR);
        //验证知识点名称排序值是否合法
        validKnowledgePoint(knowledgePoint);
        Long newParentId = knowledgePoint.getParentId();
        //验证父节点id是否合法
        if (newParentId < 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "父节点 id 非法");
        }
        Long currentId = oldKnowledgePoint.getId();
        Long oldParentId = oldKnowledgePoint.getParentId();
        //验证父节点是否相同
        if (Objects.equals(newParentId, oldParentId)) {
            return;
        }
        //验证不能将节点设置为自己的父节点
        ThrowUtils.throwIf(Objects.equals(newParentId, currentId), ErrorCode.PARAMS_ERROR, "不能将节点设置为自己的父节点");

        //验证父节点是否存在
        KnowledgePoint newParent = this.getById(newParentId);
        ThrowUtils.throwIf(newParent == null, ErrorCode.NOT_FOUND_ERROR, "父节点不存在");
        String newParentPath = newParent.getPath();

        // 不能把节点移动到自己的子孙节点下
        String pathSegment = "/" + currentId + "/";
        ThrowUtils.throwIf(newParentPath.contains(pathSegment), ErrorCode.PARAMS_ERROR, "不能将节点移动到自己的子节点下");
    }

    @Override
    public Wrapper<KnowledgePoint> getQueryWrapper(KnowledgePointQueryRequest knowledgePointQueryRequest) {
        if (knowledgePointQueryRequest == null) {
            return null;
        }
        Long id = knowledgePointQueryRequest.getId();
        Long parentId = knowledgePointQueryRequest.getParentId();
        String name = knowledgePointQueryRequest.getName();
        String path = knowledgePointQueryRequest.getPath();
        Integer level = knowledgePointQueryRequest.getLevel();
        String sortField = knowledgePointQueryRequest.getSortField();
        String sortOrder = knowledgePointQueryRequest.getSortOrder();

        LambdaQueryWrapper<KnowledgePoint> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(id != null, KnowledgePoint::getId, id)
                .eq(parentId != null, KnowledgePoint::getParentId, parentId)
                .eq(level != null, KnowledgePoint::getLevel, level)
                .like(StrUtil.isNotBlank(name), KnowledgePoint::getName, name)
                .likeRight(StrUtil.isNotBlank(path), KnowledgePoint::getPath, path);
        if (SqlUtils.validSortField(sortField) && StrUtil.isNotBlank(sortOrder)) {
            boolean isAsc = CommonConstant.SORT_ORDER_ASC.equals(sortOrder);
            queryWrapper.orderBy(true, isAsc, KnowledgePoint::getSort)
                    .orderBy(true, isAsc, KnowledgePoint::getId);
        } else {
            queryWrapper.orderByAsc(KnowledgePoint::getSort).orderByAsc(KnowledgePoint::getId);
        }
        return queryWrapper;
    }

    @Override
    public KnowledgePointVO getKnowledgePointVOWithoutChildren(KnowledgePoint knowledgePoint) {
        if (knowledgePoint == null) {
            return null;
        }
        return BeanUtil.copyProperties(knowledgePoint, KnowledgePointVO.class);
    }

    @Override
    public List<KnowledgePointVO> listKnowledgePointTree() {
        //获取所有知识点
        List<KnowledgePoint> knowledgePointList = this.list(
                new LambdaQueryWrapper<KnowledgePoint>()
                        .orderByAsc(KnowledgePoint::getSort)
                        .orderByAsc(KnowledgePoint::getId));
        if (CollUtil.isEmpty(knowledgePointList)) {
            return new ArrayList<>();
        }
        //将知识点转换为VO(不带子节点)
        List<KnowledgePointVO> knowledgePointVOList = knowledgePointList.stream()
                .map(this::getKnowledgePointVOWithoutChildren)
                .collect(Collectors.toList());
        //用map分组
        Map<Long, List<KnowledgePointVO>> childrenMap = knowledgePointVOList.stream()
                .collect(Collectors.groupingBy(KnowledgePointVO::getParentId));
        //将子节点添加到父节点的children中
        knowledgePointVOList.forEach(vo->
            {
                List<KnowledgePointVO> children = childrenMap.getOrDefault(vo.getId(), new ArrayList<>());
                sortKnowledgePointVOList(children);
                vo.setChildren(children);
            }
        );
        //获取根节点
        List<KnowledgePointVO> rootList = childrenMap.getOrDefault(0L, new ArrayList<>());
        sortKnowledgePointVOList(rootList);
        return rootList;
    }

    private void sortKnowledgePointVOList(List<KnowledgePointVO> list) {
        list.sort(Comparator.comparing(KnowledgePointVO::getSort, Comparator.nullsFirst(Integer::compareTo))
                .thenComparing(KnowledgePointVO::getId));
    }

    @Override
    public boolean removeKnowledgePointRecursively(Long id) {
        // TODO Auto-generated method stub
        
        throw new UnsupportedOperationException("Unimplemented method 'removeKnowledgePointRecursively'");
    }
}
