package com.fu.math_copilot.constant;

public interface RedisConstant {
    /**
     * 用户登录信息缓存前缀
     */
    String USER_SING_IN_REDIS_KEY_PREFIX = "user:signins";
    /**
     * Question 信息缓存前缀
     */
    String Question_DETAIL_KEY_PREFIX = "question:detail";
    /**
     * Question 信息缓存空值占位
     */
    String Question_DETAIL_NULL_PLACEHOLDER = "#NULL#";
    /**
     * Question 信息缓存锁前缀
     */
    String Question_DETAIL_LOCK_PREFIX = "question:detail:lock";

    /**
     * 获取用户登录信息缓存 key
     * @param year
     * @param userId
     * @return
     */
    static String getUserSingInRedisKey(int year,long userId)
    {
        return String.format("%s:%s:%s", USER_SING_IN_REDIS_KEY_PREFIX, year, userId);
    }
    
    static String getQuestionDetailLockKey(long questionId)
    {
        return String.format("%s:%s", Question_DETAIL_LOCK_PREFIX, questionId);
    }

    static String getQuestionDetailKey(long questionId)
    {
        return String.format("%s:%s", Question_DETAIL_KEY_PREFIX, questionId);
    }
}
