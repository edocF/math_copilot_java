package com.fu.math_copilot.esdao;

import com.fu.math_copilot.model.dto.question.QuestionEsDto;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface QuestionEsDao extends ElasticsearchRepository<QuestionEsDto, Long> {
}
