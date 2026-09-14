package com.fu.math_copilot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fu.math_copilot.common.BaseResponse;
import com.fu.math_copilot.common.DeleteRequest;
import com.fu.math_copilot.common.ErrorCode;
import com.fu.math_copilot.common.ResultUtils;
import com.fu.math_copilot.exception.ThrowUtils;
import com.fu.math_copilot.model.dto.examPaper.ExamPaperExportRequest;
import com.fu.math_copilot.model.dto.examPaper.ExamPaperGenerateRequest;
import com.fu.math_copilot.model.dto.examPaper.ExamPaperQueryRequest;
import com.fu.math_copilot.model.dto.examPaper.ExportTaskQueryRequest;
import com.fu.math_copilot.model.vo.ExamPaperDetailVO;
import com.fu.math_copilot.model.vo.ExamPaperGenerateVO;
import com.fu.math_copilot.model.vo.ExamPaperVO;
import com.fu.math_copilot.model.vo.ExportTaskVO;
import com.fu.math_copilot.service.ExamPaperService;
import com.fu.math_copilot.service.ExportTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/examPaper")
@Slf4j
@RequiredArgsConstructor
public class ExamPaperController {

    private final ExamPaperService examPaperService;
    private final ExportTaskService exportTaskService;

    /**
     * 人工组卷（传 title + questionIds）
     */
    @PostMapping("/generate")
    public BaseResponse<ExamPaperGenerateVO> generate(@RequestBody ExamPaperGenerateRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(examPaperService.generate(request));
    }

    /**
     * 按历史条件重新组卷
     */
    @PostMapping("/regenerate")
    public BaseResponse<ExamPaperGenerateVO> regenerate(@RequestBody DeleteRequest deleteRequest) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null || deleteRequest.getId() <= 0,
                ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(examPaperService.regenerate(deleteRequest.getId()));
    }

    /**
     * 试卷详情（实时题面，方案 C）
     */
    @GetMapping("/get/vo")
    public BaseResponse<ExamPaperDetailVO> getDetail(@RequestParam Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(examPaperService.getDetail(id));
    }

    /**
     * 我的试卷分页
     */
    @PostMapping("/list/my/page")
    public BaseResponse<Page<ExamPaperVO>> listMyPage(@RequestBody ExamPaperQueryRequest queryRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(examPaperService.listMyPage(queryRequest));
    }

    /**
     * 删除试卷（逻辑删除）
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deletePaper(@RequestBody DeleteRequest deleteRequest) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null || deleteRequest.getId() <= 0,
                ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(examPaperService.deletePaper(deleteRequest.getId()));
    }

    /**
     * 提交导出任务（异步；具体渲染逻辑见 ExportTaskService#executeExport）
     */
    @PostMapping("/export")
    public BaseResponse<Long> submitExport(@RequestBody ExamPaperExportRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(exportTaskService.submitExport(request));
    }

    /**
     * 查询导出进度
     */
    @GetMapping("/export/status")
    public BaseResponse<ExportTaskVO> getExportStatus(@RequestParam Long taskId) {
        ThrowUtils.throwIf(taskId == null || taskId <= 0, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(exportTaskService.getTaskStatus(taskId));
    }

    /**
     * 查看导出成功时的题面留档（snapshotJson）
     */
    @GetMapping("/export/snapshot")
    public BaseResponse<ExamPaperDetailVO> getExportSnapshot(@RequestParam Long taskId) {
        ThrowUtils.throwIf(taskId == null || taskId <= 0, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(exportTaskService.getExportSnapshot(taskId));
    }

    /**
     * 我的导出记录分页
     */
    @PostMapping("/export/list/page")
    public BaseResponse<Page<ExportTaskVO>> listExportPage(@RequestBody ExportTaskQueryRequest queryRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(exportTaskService.listMyPage(queryRequest));
    }
}
