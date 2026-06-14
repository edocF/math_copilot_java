package com.fu.math_copilot.model.dto.questionFavorite;

import lombok.Data;

import java.io.Serializable;

@Data
public class QuestionFavoriteRemoveRequest implements Serializable {

    /**
     * 题目 id
     */
    private Long questionId;

    private static final long serialVersionUID = 1L;
}
