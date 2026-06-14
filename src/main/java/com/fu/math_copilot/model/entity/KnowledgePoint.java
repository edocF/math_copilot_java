package com.fu.math_copilot.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 知识点目录树
 *
 * @TableName knowledge_point
 */
@TableName(value = "knowledge_point")
@Data
public class KnowledgePoint implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parentId;

    private String name;

    private String path;

    private Integer level;

    private Integer sort;

    private Date createTime;

    private Date updateTime;

    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
