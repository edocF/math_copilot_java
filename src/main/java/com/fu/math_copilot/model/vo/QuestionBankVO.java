package com.fu.math_copilot.model.vo;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fu.math_copilot.model.entity.Question;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class QuestionBankVO implements Serializable {


    /**
     * id
     */
    private Long id;
    /**
     * 标题
     */
    private String title;

    /**
     * 描述
     */
    private String description;

    /**
     * 图片
     */
    private String picture;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 创建用户id
     */
    private Long userId;

    /**
     * 创建用户信息
     */
    private UserVO user;

    /**
     * 题库里的题目列表（分页）
     */
    Page<Question> questionPage;


    private static final long serialVersionUID = 1L;
}
