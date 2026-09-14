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
     * 提交导出任务（创建 pending 记录，事务提交后投递到 RabbitMQ）
     */
    Long submitExport(ExamPaperExportRequest request);

    /**
     * 执行已被消费者抢占的导出任务。失败时抛出异常，由 MQ 消费编排层决定重试或终止。
     */
    void executeExport(Long taskId);

    ExportTaskVO getTaskStatus(Long taskId);

    ExamPaperDetailVO getExportSnapshot(Long taskId);

    Page<ExportTaskVO> listMyPage(ExportTaskQueryRequest queryRequest);

    // ---------- 导出过程内部状态更新 ----------

    ExportTask getTaskForExport(Long taskId);

    void markProcessing(Long taskId);

    void updateProgress(Long taskId, int progress);

    void markSuccess(Long taskId, String fileUrl, String fileKey, String snapshotJson);

    void markFailed(Long taskId, String errorMsg);
}
