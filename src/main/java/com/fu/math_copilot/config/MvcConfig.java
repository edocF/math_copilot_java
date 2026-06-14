package com.fu.math_copilot.config;

import com.fu.math_copilot.interceptors.UserInfoInterceptor;
import com.fu.math_copilot.utils.JwtUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class MvcConfig implements WebMvcConfigurer {
    private final JwtUtils jwtUtils;
    private final AuthProperties authProperties;
    public MvcConfig(JwtUtils jwtUtils, AuthProperties authProperties) {
        this.jwtUtils = jwtUtils;
        this.authProperties = authProperties;
    }
    @Override
    public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
        registry.addInterceptor(new UserInfoInterceptor(jwtUtils))
                .addPathPatterns("/**")
                .excludePathPatterns(authProperties.getExcludedPath())
                .excludePathPatterns(authProperties.getIncludedPath())
                .excludePathPatterns(
                "/error",
                "/favicon.ico",
                "/v2/**",
                "/v3/**",
                "/swagger-resources/**",
                "/webjars/**",
                "/doc.html"
        );
    }

}
