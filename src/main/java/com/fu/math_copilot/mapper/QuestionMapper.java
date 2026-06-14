package com.fu.math_copilot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fu.math_copilot.model.entity.Question;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;


/**
* @author lenovo
* @description 针对表【question(题目)】的数据库操作Mapper
* @createDate 2026-03-15 18:48:45
* @Entity generator.domain.Question
*/
public interface QuestionMapper extends BaseMapper<Question> {

    @Select("SELECT * FROM question where updateTime > #{fiveMinutesAgo}")
    List<Question> getQuestionWithDeleted(Date fiveMinutesAgo);
}




