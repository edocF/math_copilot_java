package com.fu.math_copilot.service.impl;

import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import com.fu.math_copilot.config.QuestionCacheProperties;
import com.fu.math_copilot.constant.RedisConstant;
import com.fu.math_copilot.model.entity.Question;
import com.fu.math_copilot.service.QuestionCacheService;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import io.netty.util.internal.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.TimeUnit;
@Service
@Slf4j
@RequiredArgsConstructor
public class QuestionCacheServiceImpl implements QuestionCacheService {

    private final RedissonClient redissonClient;
    private final QuestionCacheProperties cacheProperties;
    /**
     * 缓存未命中标志
     */
    private final static Question MISS = new Question();
    @Override
    public Question getFromCache(Long questionId, QuestionLoader loader) {
        //校验参数
        if (questionId == null || questionId <= 0) {
            return null;
        }
        //查看缓存是否开启
        if (!cacheProperties.isEnabled()) {
            return loader.load(questionId);
        }
        //获取缓存key
        Question result = null;
        String key = RedisConstant.getQuestionDetailKey(questionId);
        RBucket<String> bucket = redissonClient.getBucket(key);
        try {
            String json = bucket.get();
            result = readCachedQuestion(json, questionId);
            if (result != MISS) {
                return result;
            }
        }
        catch(Exception e)
        {
            log.warn("question cache read failed, fallback to db, id={}", questionId, e);
            return loader.load(questionId);
        }
        //缓存未命中
        // 防止缓存击穿：使用分布式锁，仅一个线程加载，其他线程等待或快速返回
        String lockKey = RedisConstant.getQuestionDetailLockKey(questionId);
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!locked) {
                // 没拿到锁，自旋尝试再次从缓存获取
                Thread.sleep(100); // 简单退让，防止过度打击缓存/DB
                String json = bucket.get();
                result = readCachedQuestion(json, questionId);
                if (result != MISS) {
                    return result;
                }
                // 还未命中，递归继续自旋
                return getFromCache(questionId, loader);
            }
            // 拿到锁后，应该再次检查缓存，防止并发下重复回源DB
            String json = bucket.get();
            result = readCachedQuestion(json, questionId);
            if (result != MISS) {
                return result;
            }
            // 确定缓存未命中，从数据库加载
            result = loader.load(questionId);
            if (result == null) {
                // 缓存空值，防止缓存穿透
                bucket.set(RedisConstant.Question_DETAIL_NULL_PLACEHOLDER,
                           cacheProperties.getNullTtlSeconds(), TimeUnit.SECONDS);
            } else {
                // 设置缓存并加上一定的随机过期
                long ttl = cacheProperties.getTtlSeconds() +
                        ThreadLocalRandom.current().nextLong(0, cacheProperties.getTtlJitterSeconds() + 1);
                bucket.set(JSONUtil.toJsonStr(result), ttl, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("question cache lock or write failed, id={}", questionId, e);
            // 兜底回源
            return loader.load(questionId);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        return result;

    }
    private Question readCachedQuestion(String json, Long questionId) {
        if (RedisConstant.Question_DETAIL_NULL_PLACEHOLDER.equals(json)) {
            log.debug("question cache null-hit, id={}", questionId);
            return null; // 注意：要和 MISS 区分
        }
        if (StrUtil.isNotBlank(json)) {
            log.debug("question cache hit, id={}", questionId);
            return JSONUtil.toBean(json, Question.class);
        }
        return MISS; // 静态 final 哨兵对象，表示「未命中」
    }
    @Override
    public void evict(Long questionId) {
       if(questionId == null || questionId <= 0 || !cacheProperties.isEnabled())
       {
        return;
       }
       try {
        redissonClient.getBucket(RedisConstant.getQuestionDetailKey(questionId)).delete();
        log.debug("question cache evicted, id={}", questionId);
       }
       catch(Exception e)
       {
          log.warn("question cache evict failed, id={}", questionId, e);
       }
    }
    
}
