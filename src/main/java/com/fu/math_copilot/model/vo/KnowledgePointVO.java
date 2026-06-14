package com.fu.math_copilot.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
public class KnowledgePointVO implements Serializable {

    private Long id;

    private Long parentId;

    private String name;

    private String path;

    private Integer level;

    private Integer sort;

    private Date createTime;

    private Date updateTime;

    /**
     * 树形结构子节点
     */
    private List<KnowledgePointVO> children;

    private static final long serialVersionUID = 1L;
}
