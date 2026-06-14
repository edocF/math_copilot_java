package com.fu.math_copilot.aop;

import com.fu.math_copilot.annotation.AuthCheck;
import com.fu.math_copilot.common.ErrorCode;
import com.fu.math_copilot.exception.BusinessException;
import com.fu.math_copilot.model.entity.User;
import com.fu.math_copilot.model.enums.UserRoleEnum;
import com.fu.math_copilot.service.UserService;
import com.fu.math_copilot.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 权限校验 AOP
 *
 * @author <a href="https://github.com/edocF">edocF</a>
 * @from <a href="https://fu.icu"></a>
 */
@Aspect
@Component
@RequiredArgsConstructor
public class AuthInterceptor {


    private  final UserService userService;

    /**
     * 执行拦截
     *
     * @param joinPoint
     * @param authCheck
     * @return
     */
    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        String mustRole =  authCheck.mustRole();
        UserRoleEnum mustRoleEnum = UserRoleEnum.getEnumByValue(mustRole);
        // 当前登录用户
        User loginUser = userService.getById(UserContext.getCurrentUserId());
        // 不需要权限，放行
        if(mustRoleEnum == null)
        {
            return joinPoint.proceed();
        }
        // 必须有该权限才通过
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(loginUser.getUserRole());
        // 如果被封号，直接拒绝
        if(UserRoleEnum.BAN.equals(userRoleEnum))
        {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 必须有管理员权限
        if(UserRoleEnum.ADMIN.equals(mustRoleEnum) && !UserRoleEnum.ADMIN.equals(userRoleEnum))
        {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 通过权限校验，放行
        return joinPoint.proceed();

    }
}

