package com.fu.math_copilot.exception;

import com.fu.math_copilot.common.BaseResponse;
import com.fu.math_copilot.common.ErrorCode;
import com.fu.math_copilot.common.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 *
 * @author <a href="https://github.com/edocF">edocF</a>
 * @from <a href="https://fu.icu"></a>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException ex)
    {
        log.error("businessException: " + ex.getMessage(), ex);
        return ResultUtils.error(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException ex)
    {
        log.error("runtimeException: " + ex.getMessage(), ex);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }

}
