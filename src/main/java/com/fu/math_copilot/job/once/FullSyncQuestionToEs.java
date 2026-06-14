package com.fu.math_copilot.job.once;

import cn.hutool.core.collection.CollUtil;

import com.fu.math_copilot.esdao.QuestionEsDao;
import com.fu.math_copilot.model.entity.Question;
import com.fu.math_copilot.service.QuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;

import java.util.List;
import java.util.stream.Collectors;

import com.fu.math_copilot.model.dto.question.QuestionEsDto;



//@Component
@Slf4j
@RequiredArgsConstructor
public class FullSyncQuestionToEs implements CommandLineRunner {


    private final QuestionService questionService;

    private final QuestionEsDao questionEsDao;
    @Override
    public void run(String... args) throws Exception {
         List<Question> questionList = questionService.list();
         if (CollUtil.isEmpty(questionList)) {
             log.info("no question");
             return;
         }
        List<QuestionEsDto> esDtolist = questionList.stream().map(QuestionEsDto::objectToDto).collect(Collectors.toList());

        int pageSize = 500;
        int total = esDtolist.size();
        log.info("FullSyncQuestionToEs start, total {}", total);
        for(int current = 0; current < total; current += pageSize) {
            int size = Math.min(pageSize, total - current);
            List<QuestionEsDto> subQuestionEsDto = esDtolist.subList(current, current + size);
            questionEsDao.saveAll(subQuestionEsDto);
        }
        log.info("FullSyncQuestionToEs end, total {}", total);
    }
}
