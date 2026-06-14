package com.fu.math_copilot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fu.math_copilot.model.dto.examPaper.ExamPaperExportRequest;
import com.fu.math_copilot.model.dto.examPaper.ExportTaskQueryRequest;
import com.fu.math_copilot.model.entity.ExportTask;
import com.fu.math_copilot.model.vo.ExamPaperDetailVO;
import com.fu.math_copilot.model.vo.ExportTaskVO;

/**
 * 试卷导出任务 Service
 */
public interface ExportTaskService extends IService<ExportTask> {

    /**
     * 提交导出任务（创建 pending 记录；异步执行由 {@link #executeExportAsync} 负责，待你实现）
     */
    Long submitExport(ExamPaperExportRequest request);

    /**
     * 异步执行导出 —— <b>留空供你实现</b>（freemarker 渲染 → 生成文件 → 上传 COS → 写 snapshotJson）
     */
    void executeExportAsync(Long taskId);

    ExportTaskVO getTaskStatus(Long taskId);

    ExamPaperDetailVO getExportSnapshot(Long taskId);

    Page<ExportTaskVO> listMyPage(ExportTaskQueryRequest queryRequest);

    // ---------- 以下供你在 executeExportAsync 中调用 ----------

    ExportTask getTaskForExport(Long taskId);

    void markProcessing(Long taskId);

    void updateProgress(Long taskId, int progress);

    void markSuccess(Long taskId, String fileUrl, String fileKey, String snapshotJson);

    void markFailed(Long taskId, String errorMsg);
}
