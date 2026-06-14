package com.fu.math_copilot.model.dto.questionBank;


import com.fu.math_copilot.common.PageRequest;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class QuestionBankQueryRequest extends PageRequest implements Serializable {
    /**
     * id
     */
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 搜索词
     */
    private String searchText;

    /**
     * 描述
     */
    private String description;

    /**
     * 图片
     */
    private String picture;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 编辑时间
     */
    private Date editTime;

    /**
     * 创建时间
     */
    private Date createTime;


    private static final long serialVersionUID = 1L;
}
