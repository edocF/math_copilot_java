package com.fu.math_copilot.model.dto.questionBank;

import com.fu.math_copilot.common.PageRequest;
import lombok.Data;

import java.io.Serializable;

@Data
public class QuestionBankDetailRequest extends PageRequest implements Serializable {
    /**
     * id
     */
    private Long id;
    private static final long serialVersionUID = 1L;
}
