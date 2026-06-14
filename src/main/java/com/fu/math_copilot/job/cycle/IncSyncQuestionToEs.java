package com.fu.math_copilot.job.cycle;



import com.fu.math_copilot.esdao.QuestionEsDao;
import com.fu.math_copilot.mapper.QuestionMapper;
import com.fu.math_copilot.model.dto.question.QuestionEsDto;
import com.fu.math_copilot.model.entity.Question;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;


import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;


// todo 取消注释开启任务
//@Component
@Slf4j
@RequiredArgsConstructor
public class IncSyncQuestionToEs {


    private final QuestionMapper questionMapper;


    private final QuestionEsDao questionEsDao;

    /**
     * 每分钟执行一次
     */
    @Scheduled(fixedRate = 60 * 1000)
    public void run() {
        // 查询近 5 分钟内的数据
        long fiveMinutes = 5 * 60 * 1000;
        Date fiveMinutesAgo = new Date(new Date().getTime() - fiveMinutes);
        List<Question> questions= questionMapper.getQuestionWithDeleted(fiveMinutesAgo);
        List<QuestionEsDto> questionEsDto = questions.stream().map(QuestionEsDto::objectToDto).collect(Collectors.toList());
        int pageSize = 500;
        int total = questionEsDto.size();
        log.info("FullSyncQuestionToEs start, total {}", total);
        for(int current = 0; current < total; current += pageSize) {
            int size = Math.min(pageSize, total - current);
            List<QuestionEsDto> subQuestionEsDto = questionEsDto.subList(current, current + size);
            questionEsDao.saveAll(subQuestionEsDto);
        }
        log.info("FullSyncQuestionToEs end, total {}", total);

    }
}