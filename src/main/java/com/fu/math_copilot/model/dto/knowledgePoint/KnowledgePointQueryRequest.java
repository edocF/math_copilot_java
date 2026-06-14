package com.fu.math_copilot.model.dto.knowledgePoint;

import com.fu.math_copilot.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@Data
@EqualsAndHashCode(callSuper = false)
public class KnowledgePointQueryRequest extends PageRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 父节点 id
     */
    private Long parentId;

    /**
     * 名称（模糊查询）
     */
    private String name;

    /**
     * 路径（前缀匹配）
     */
    private String path;

    /**
     * 层级
     */
    private Integer level;

    private static final long serialVersionUID = 1L;
}
