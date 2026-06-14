package com.fu.math_copilot.interceptors;


import com.fu.math_copilot.utils.JwtUtils;
import com.fu.math_copilot.utils.UserContext;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class UserInfoInterceptor implements HandlerInterceptor {
    private final JwtUtils jwtUtils;
    public UserInfoInterceptor(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
           String jwtToken = request.getHeader("token");
           Long userId = jwtUtils.parseToken(jwtToken);
           UserContext.setCurrentUserId(userId);
           return true;
    }
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserContext.clear();
        return;
    }
}
