package com.fu.math_copilot.service;

import com.fu.math_copilot.common.ErrorCode;
import com.fu.math_copilot.exception.BusinessException;
import com.fu.math_copilot.manager.CosManager;
import com.fu.math_copilot.manager.GotenbergClient;
import com.fu.math_copilot.mapper.ExportTaskMapper;
import com.fu.math_copilot.model.entity.ExportTask;
import com.fu.math_copilot.model.vo.ExamPaperDetailVO;
import com.fu.math_copilot.service.impl.ExportTaskServiceImpl;
import com.fu.math_copilot.utils.ExamPaperExportHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportTaskExecutionTest {

    @Mock
    private ExamPaperService examPaperService;
    @Mock
    private UserService userService;
    @Mock
    private CosManager cosManager;
    @Mock
    private ExamPaperExportHelper exportHelper;
    @Mock
    private GotenbergClient gotenbergClient;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ExportTaskMapper mapper;

    private ExportTaskServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ExportTaskServiceImpl(
                examPaperService, userService, cosManager, exportHelper,
                gotenbergClient, eventPublisher);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    @Test
    void executionFailureEscapesWithoutChoosingTerminalState() {
        ExportTask task = new ExportTask();
        task.setId(42L);
        task.setPaperId(7L);
        task.setUserId(9L);
        task.setContentScope("question");
        task.setStatus("processing");
        when(mapper.selectById(42L)).thenReturn(task);

        ExamPaperDetailVO detail = new ExamPaperDetailVO();
        when(examPaperService.buildDetailForExport(7L, "question")).thenReturn(detail);
        when(exportHelper.renderHtml(detail)).thenReturn("<html></html>");
        BusinessException failure = new BusinessException(
                ErrorCode.SYSTEM_ERROR, "PDF 服务不可用");
        when(gotenbergClient.convertHtmlToPdf("<html></html>")).thenThrow(failure);

        BusinessException thrown = assertThrows(
                BusinessException.class, () -> service.executeExport(42L));

        assertSame(failure, thrown);
        verify(mapper, never()).updateById(argThat(update ->
                "failed".equals(update.getStatus())));
    }
}
