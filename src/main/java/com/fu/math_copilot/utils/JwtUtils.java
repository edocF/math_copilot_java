package com.fu.math_copilot.utils;

import cn.hutool.core.exceptions.ValidateException;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTValidator;
import cn.hutool.jwt.signers.JWTSigner;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.fu.math_copilot.common.ErrorCode;
import com.fu.math_copilot.config.JwtProperties;
import com.fu.math_copilot.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.util.Date;

@Component
public class JwtUtils {
    private final JWTSigner jwtSigner;
    @Autowired
    private final JwtProperties properties;
    public JwtUtils(KeyPair keyPair, JwtProperties properties) {
        this.jwtSigner = JWTSignerUtil.createSigner("rs256",keyPair);
        this.properties = properties;
    }
    /**
     * 创建 access-token
     *
     * @param userId 用户信息
     * @return access-token
     */
    public String createToken(Long userId) {
        // 1.生成jws
        return JWT.create()
                .setPayload("user", userId)
                .setExpiresAt(new Date(System.currentTimeMillis() + properties.getTokenTTL().toMillis()))
                .setSigner(jwtSigner)
                .sign();
    }

    /**
     * 解析token
     *
     * @param token token
     * @return 解析刷新token得到的用户信息
     */
    public Long parseToken(String token) {
        // 1.校验token是否为空
        if (token == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 2.校验并解析jwt
        JWT jwt;
        try {
            jwt = JWT.of(token).setSigner(jwtSigner);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR);
        }
        // 2.校验jwt是否有效
        if (!jwt.verify()) {
            // 验证失败
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR);
        }
        // 3.校验是否过期
        try {
            JWTValidator.of(jwt).validateDate();
        } catch (ValidateException e) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR);
        }
        // 4.数据格式校验
        Object userPayload = jwt.getPayload("user");
        if (userPayload == null) {
            // 数据为空
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR);
        }

        // 5.数据解析
        try {
            return Long.valueOf(userPayload.toString());
        } catch (RuntimeException e) {
            // 数据格式有误
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR);
        }
    }
}
