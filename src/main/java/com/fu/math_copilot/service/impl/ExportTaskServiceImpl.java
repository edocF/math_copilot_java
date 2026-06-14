package com.fu.math_copilot.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fu.math_copilot.common.ErrorCode;
import com.fu.math_copilot.constant.UserConstant;
import com.fu.math_copilot.exception.BusinessException;
import com.fu.math_copilot.exception.ThrowUtils;
import cn.hutool.core.collection.CollUtil;
import com.fu.math_copilot.constant.FileConstant;
import com.fu.math_copilot.manager.CosManager;
import com.fu.math_copilot.manager.GotenbergClient;
import com.fu.math_copilot.mapper.ExportTaskMapper;
import com.fu.math_copilot.model.dto.examPaper.ExamPaperExportRequest;
import com.fu.math_copilot.model.dto.examPaper.ExportTaskQueryRequest;
import com.fu.math_copilot.model.entity.ExamPaper;
import com.fu.math_copilot.model.entity.ExportTask;
import com.fu.math_copilot.model.entity.User;
import com.fu.math_copilot.model.enums.ExportFormatEnum;
import com.fu.math_copilot.model.enums.ExportScopeEnum;
import com.fu.math_copilot.model.enums.ExportStatusEnum;
import com.fu.math_copilot.model.vo.ExamPaperDetailVO;
import com.fu.math_copilot.model.vo.ExportTaskVO;
import com.fu.math_copilot.service.ExamPaperService;
import com.fu.math_copilot.service.ExportTaskService;
import com.fu.math_copilot.service.UserService;
import com.fu.math_copilot.event.ExportTaskSubmittedEvent;
import com.fu.math_copilot.utils.ExamPaperExportHelper;
import com.fu.math_copilot.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.Date;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExportTaskServiceImpl extends ServiceImpl<ExportTaskMapper, ExportTask>
        implements ExportTaskService {

    private final ExamPaperService examPaperService;
    private final UserService userService;
    private final CosManager cosManager;
    private final ExamPaperExportHelper examPaperExportHelper;
    private final GotenbergClient gotenbergClient;
    private final ApplicationEventPublisher applicationEventPublisher;
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitExport(ExamPaperExportRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        Long paperId = request.getPaperId();
        ThrowUtils.throwIf(paperId == null || paperId <= 0, ErrorCode.PARAMS_ERROR);

        String format = StrUtil.blankToDefault(request.getFormat(), ExportFormatEnum.PDF.getValue());
        String contentScope = StrUtil.blankToDefault(request.getContentScope(), ExportScopeEnum.QUESTION.getValue());
        ThrowUtils.throwIf(!ExportFormatEnum.PDF.getValue().equals(format), ErrorCode.PARAMS_ERROR, "当前仅支持 PDF 导出");
        ThrowUtils.throwIf(ExportScopeEnum.getEnumByValue(contentScope) == null, ErrorCode.PARAMS_ERROR, "导出范围非法");

        ExamPaper paper = examPaperService.getById(paperId);
        ThrowUtils.throwIf(paper == null, ErrorCode.NOT_FOUND_ERROR, "试卷不存在");
        Long userId = getLoginUserId();
        checkPaperAccess(paper, userId);
        // 幂等性检查
        String idempotentKey = DigestUtil.md5Hex(paperId + ":" + format + ":" + contentScope);
        // 检查是否存在相同任务
        try{
            ExportTask existed = this.getOne(new LambdaQueryWrapper<ExportTask>()
                    .eq(ExportTask::getIdempotentKey, idempotentKey));
            if (existed != null && !ExportStatusEnum.FAILED.getValue().equals(existed.getStatus())) {
                Long existedId = existed.getId();
                if (ExportStatusEnum.PENDING.getValue().equals(existed.getStatus())) {
                    applicationEventPublisher.publishEvent(new ExportTaskSubmittedEvent(this, existedId));
                    log.info("幂等命中 pending 任务，重新调度导出, taskId={}", existedId);
                }
                return existedId;
            }
        } catch (Exception e) {
            log.error("检查幂等性时发生异常: {}", e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "检查幂等性时发生异常");
        }

        ExportTask task = new ExportTask();
        task.setPaperId(paperId);
        task.setUserId(userId);
        task.setFormat(format);
        task.setContentScope(contentScope);
        task.setStatus(ExportStatusEnum.PENDING.getValue());
        task.setProgress(0);
        task.setIdempotentKey(idempotentKey);
        boolean saved = this.save(task);
        ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR);

        Long taskId = task.getId();
        applicationEventPublisher.publishEvent(new ExportTaskSubmittedEvent(this, taskId));
        log.info("导出任务提交成功, taskId={}", taskId);
        return taskId;
    }

    /**
     * 异步导出：查题面 → FreeMarker 渲染 HTML → Gotenberg 转 PDF → 上传 COS → 写 snapshotJson
     */
    @Override
    public void executeExportAsync(Long taskId) {
        ExportTask task = this.getById(taskId);
        if (task == null) {
            log.warn("导出任务不存在, taskId={}", taskId);
            return;
        }
        if (!ExportStatusEnum.PENDING.getValue().equals(task.getStatus())) {
            log.warn("导出任务非 pending，跳过执行, taskId={}, status={}", taskId, task.getStatus());
            return;
        }

        File tempFile = null;
        try {
            markProcessing(taskId);

            ExamPaperDetailVO paper = examPaperService.buildDetailForExport(
                    task.getPaperId(), task.getContentScope());
            if (CollUtil.isNotEmpty(paper.getMissingQuestionIds())) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR,
                        "存在已下架题目，请重新组卷后再导出");
            }
            paper.setExportedAt(new Date());
            String snapshotJson = JSONUtil.toJsonStr(paper);

            updateProgress(taskId, 30);
            String html = examPaperExportHelper.renderHtml(paper);
            updateProgress(taskId, 50);
            byte[] pdfBytes = gotenbergClient.convertHtmlToPdf(html);
            tempFile = FileUtil.createTempFile("exam_export_", ".pdf", true);
            FileUtil.writeBytes(pdfBytes, tempFile);

            updateProgress(taskId, 70);
            String fileKey = String.format("/exam_export/%s/%s.pdf",
                    task.getUserId(), taskId);
            cosManager.putObject(fileKey, tempFile);
            String fileUrl = FileConstant.COS_HOST + fileKey;

            markSuccess(taskId, fileUrl, fileKey, snapshotJson);
            log.info("导出任务完成, taskId={}, format={}", taskId, task.getFormat());
        } catch (Exception e) {
            String message = e instanceof BusinessException
                    ? e.getMessage()
                    : "导出失败，请稍后重试";
            markFailed(taskId, message);
            log.error("导出任务失败, taskId={}", taskId, e);
        } finally {
            if (tempFile != null && tempFile.exists() && !tempFile.delete()) {
                log.warn("导出临时文件删除失败, path={}", tempFile.getAbsolutePath());
            }
        }
    }

    @Override
    public ExportTaskVO getTaskStatus(Long taskId) {
        ExportTask task = getTaskOrThrow(taskId);
        checkTaskAccess(task);
        return toExportTaskVO(task);
    }

    @Override
    public ExamPaperDetailVO getExportSnapshot(Long taskId) {
        ExportTask task = getTaskOrThrow(taskId);
        checkTaskAccess(task);
        ThrowUtils.throwIf(!ExportStatusEnum.SUCCESS.getValue().equals(task.getStatus()),
                ErrorCode.OPERATION_ERROR, "导出尚未成功，暂无留档");
        ThrowUtils.throwIf(StrUtil.isBlank(task.getSnapshotJson()), ErrorCode.NOT_FOUND_ERROR, "导出留档不存在");
        return JSONUtil.toBean(task.getSnapshotJson(), ExamPaperDetailVO.class);
    }

    @Override
    public Page<ExportTaskVO> listMyPage(ExportTaskQueryRequest queryRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        Long userId = getLoginUserId();
        int current = queryRequest.getCurrent();
        int pageSize = queryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize >= 20, ErrorCode.PARAMS_ERROR, "请求过多");

        LambdaQueryWrapper<ExportTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExportTask::getUserId, userId)
                .eq(queryRequest.getPaperId() != null, ExportTask::getPaperId, queryRequest.getPaperId())
                .eq(StrUtil.isNotBlank(queryRequest.getStatus()), ExportTask::getStatus, queryRequest.getStatus())
                .orderByDesc(ExportTask::getCreateTime);

        Page<ExportTask> page = this.page(new Page<>(current, pageSize), wrapper);
        Page<ExportTaskVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::toExportTaskVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public ExportTask getTaskForExport(Long taskId) {
        return getTaskOrThrow(taskId);
    }

    @Override
    public void markProcessing(Long taskId) {
        ExportTask update = new ExportTask();
        update.setId(taskId);
        update.setStatus(ExportStatusEnum.PROCESSING.getValue());
        update.setProgress(10);
        this.updateById(update);
    }

    @Override
    public void updateProgress(Long taskId, int progress) {
        ExportTask update = new ExportTask();
        update.setId(taskId);
        update.setProgress(Math.min(100, Math.max(0, progress)));
        this.updateById(update);
    }

    @Override
    public void markSuccess(Long taskId, String fileUrl, String fileKey, String snapshotJson) {
        ExportTask update = new ExportTask();
        update.setId(taskId);
        update.setStatus(ExportStatusEnum.SUCCESS.getValue());
        update.setProgress(100);
        update.setFileUrl(fileUrl);
        update.setFileKey(fileKey);
        update.setSnapshotJson(snapshotJson);
        update.setErrorMsg(null);
        this.updateById(update);
    }

    @Override
    public void markFailed(Long taskId, String errorMsg) {
        ExportTask update = new ExportTask();
        update.setId(taskId);
        update.setStatus(ExportStatusEnum.FAILED.getValue());
        update.setErrorMsg(StrUtil.sub(errorMsg, 0, 500));
        this.updateById(update);
    }

    private ExportTask getTaskOrThrow(Long taskId) {
        ThrowUtils.throwIf(taskId == null || taskId <= 0, ErrorCode.PARAMS_ERROR);
        ExportTask task = this.getById(taskId);
        ThrowUtils.throwIf(task == null, ErrorCode.NOT_FOUND_ERROR, "导出任务不存在");
        return task;
    }

    private void checkTaskAccess(ExportTask task) {
        Long userId = getLoginUserId();
        if (task.getUserId().equals(userId)) {
            return;
        }
        User user = userService.getById(userId);
        if (user != null && UserConstant.ADMIN_ROLE.equals(user.getUserRole())) {
            return;
        }
        throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
    }

    private void checkPaperAccess(ExamPaper paper, Long userId) {
        if (paper.getUserId().equals(userId)) {
            return;
        }
        User user = userService.getById(userId);
        if (user != null && UserConstant.ADMIN_ROLE.equals(user.getUserRole())) {
            return;
        }
        throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
    }

    private ExportTaskVO toExportTaskVO(ExportTask task) {
        ExportTaskVO vo = BeanUtil.copyProperties(task, ExportTaskVO.class);
        if (ExportStatusEnum.SUCCESS.getValue().equals(task.getStatus())
                && StrUtil.isNotBlank(task.getFileKey())) {
            vo.setFileUrl(cosManager.buildDownloadUrl(task.getFileKey()));
        }
        return vo;
    }

    private Long getLoginUserId() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return userId;
    }
}
