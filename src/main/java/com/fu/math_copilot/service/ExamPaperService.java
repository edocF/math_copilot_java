package com.fu.math_copilot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fu.math_copilot.model.dto.examPaper.ExamPaperGenerateRequest;
import com.fu.math_copilot.model.dto.examPaper.ExamPaperQueryRequest;
import com.fu.math_copilot.model.entity.ExamPaper;
import com.fu.math_copilot.model.vo.ExamPaperDetailVO;
import com.fu.math_copilot.model.vo.ExamPaperGenerateVO;
import com.fu.math_copilot.model.vo.ExamPaperVO;

/**
 * 试卷（组卷）Service
 */
public interface ExamPaperService extends IService<ExamPaper> {

    ExamPaperGenerateVO generate(ExamPaperGenerateRequest request);

    ExamPaperGenerateVO regenerate(Long paperId);

    ExamPaperDetailVO getDetail(Long paperId);

    /**
     * 导出渲染用：实时查 question 组装 VO（方案 C，由导出任务在异步线程中调用）
     */
    ExamPaperDetailVO buildDetailForExport(Long paperId, String contentScope);

    Page<ExamPaperVO> listMyPage(ExamPaperQueryRequest queryRequest);

    boolean deletePaper(Long paperId);
}
