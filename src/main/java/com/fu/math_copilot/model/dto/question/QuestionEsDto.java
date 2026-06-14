package com.fu.math_copilot.model.dto.question;

import cn.hutool.core.bean.BeanUtil;
import com.fu.math_copilot.model.entity.Question;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;
import java.util.Date;

@Document(indexName = "question")
@Data
public class QuestionEsDto implements Serializable {
    /**
     * 时间格式化模式
     */
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    /**
     * id
     */
    @Id
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 推荐答案
     */
    private String answer;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 创建时间
     */
    @Field(type = FieldType.Date, format = {}, pattern = DATE_TIME_PATTERN)
    private Date createTime;

    /**
     * 更新时间
     */
    @Field(type = FieldType.Date, format = {}, pattern = DATE_TIME_PATTERN)
    private Date updateTime;

    /**
     * 编辑时间
     */
    @Field(type = FieldType.Date, format = {}, pattern = DATE_TIME_PATTERN)
    private Date editTime;

    /**
     * 是否删除
     */
    private Integer isDelete;

    private static final long serialVersionUID = 1L;

    /**
     * 对像转包装类
     * @param questionEsDto
     * @return
     */
    public static Question dtoToObject(QuestionEsDto questionEsDto) {
        Question question = new Question();
        BeanUtil.copyProperties(questionEsDto, question);
        return question;
    }
    /**
     * 对象转包装类
     *
     * @param question
     * @return
     */
    public static QuestionEsDto objectToDto(Question question) {
        QuestionEsDto questionEsDto = new QuestionEsDto();
        BeanUtil.copyProperties(question, questionEsDto);
        return questionEsDto;
    }
}
