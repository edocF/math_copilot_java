package com.fu.math_copilot.model.dto.knowledgePoint;

import lombok.Data;

import java.io.Serializable;

@Data
public class KnowledgePointAddRequest implements Serializable {

    /**
     * 父节点 id，根节点可传 0
     */
    private Long parentId;

    /**
     * 知识点名称
     */
    private String name;

    /**
     * 同级排序
     */
    private Integer sort;

    private static final long serialVersionUID = 1L;
}
