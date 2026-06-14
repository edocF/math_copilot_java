# 06 - 代码实战（一）：读路径与 QuestionCacheService

> 本章带你在项目中**新建缓存服务**，实现 Cache-Aside 读路径。  
> 建议打开 IDE 对照操作。

---

## 1. 目标文件清单

```
constant/RedisConstant.java              ← 扩展
config/QuestionCacheProperties.java      ← 新建（配置开关）
service/QuestionCacheService.java        ← 新建接口
service/impl/QuestionCacheServiceImpl.java ← 新建实现
service/QuestionService.java             ← 加方法
service/impl/QuestionServiceImpl.java    ← 委托缓存
```

---

## 2. Step 1：扩展 RedisConstant

`src/main/java/.../constant/RedisConstant.java`

在接口末尾增加：

```java
/** 题目详情缓存 key 前缀 */
String QUESTION_DETAIL_KEY_PREFIX = "question:detail";

/** DB 无记录时的占位值 */
String QUESTION_DETAIL_NULL_PLACEHOLDER = "#NULL#";

long QUESTION_DETAIL_TTL_SECONDS = 30 * 60L;
long QUESTION_DETAIL_TTL_JITTER_SECONDS = 5 * 60L;
long QUESTION_DETAIL_NULL_TTL_SECONDS = 60L;

static String getQuestionDetailKey(long questionId) {
    return QUESTION_DETAIL_KEY_PREFIX + ":" + questionId;
}
```

---

## 3. Step 2：配置类 QuestionCacheProperties

`config/QuestionCacheProperties.java`

```java
package com.fu.math_copilot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "cache.question")
public class QuestionCacheProperties {

    /** 无 Redis 或压测对比时可关 */
    private boolean enabled = true;

    private long ttlSeconds = 30 * 60L;
    private long ttlJitterSeconds = 5 * 60L;
    private long nullTtlSeconds = 60L;
}
```

`application.yml` 增加：

```yaml
cache:
  question:
    enabled: true
    ttl-seconds: 1800
    ttl-jitter-seconds: 300
    null-ttl-seconds: 60
```

---

## 4. Step 3：QuestionCacheService 接口

```java
package com.fu.math_copilot.service;

import com.fu.math_copilot.model.entity.Question;

public interface QuestionCacheService {

    /**
     * Cache-Aside 读：先 Redis，miss 则 loader 回源 DB 并回填
     */
    Question getFromCache(Long questionId, QuestionLoader loader);

    void evict(Long questionId);

    @FunctionalInterface
    interface QuestionLoader {
        Question load(Long questionId);
    }
}
```

用 `QuestionLoader` 避免 `QuestionCacheService` 和 `QuestionService` 循环依赖：缓存层不直接调 `getById`，由调用方传入 lambda。

---

## 5. Step 4：QuestionCacheServiceImpl 实现

```java
package com.fu.math_copilot.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.fu.math_copilot.config.QuestionCacheProperties;
import com.fu.math_copilot.constant.RedisConstant;
import com.fu.math_copilot.model.entity.Question;
import com.fu.math_copilot.service.QuestionCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionCacheServiceImpl implements QuestionCacheService {

    private final RedissonClient redissonClient;
    private final QuestionCacheProperties cacheProperties;

    @Override
    public Question getFromCache(Long questionId, QuestionLoader loader) {
        if (questionId == null || questionId <= 0) {
            return null;
        }
        if (!cacheProperties.isEnabled()) {
            return loader.load(questionId);
        }

        String key = RedisConstant.getQuestionDetailKey(questionId);
        RBucket<String> bucket = redissonClient.getBucket(key);

        try {
            String json = bucket.get();
            if (RedisConstant.QUESTION_DETAIL_NULL_PLACEHOLDER.equals(json)) {
                log.debug("question cache null-hit, id={}", questionId);
                return null;
            }
            if (StrUtil.isNotBlank(json)) {
                log.debug("question cache hit, id={}", questionId);
                return JSONUtil.toBean(json, Question.class);
            }
        } catch (Exception e) {
            log.warn("question cache read failed, fallback to db, id={}", questionId, e);
            return loader.load(questionId);
        }

        // cache miss
        log.debug("question cache miss, id={}", questionId);
        Question question = loader.load(questionId);
        try {
            if (question == null) {
                bucket.set(RedisConstant.QUESTION_DETAIL_NULL_PLACEHOLDER,
                        cacheProperties.getNullTtlSeconds(), TimeUnit.SECONDS);
            } else {
                long ttl = cacheProperties.getTtlSeconds()
                        + ThreadLocalRandom.current().nextLong(0, cacheProperties.getTtlJitterSeconds() + 1);
                bucket.set(JSONUtil.toJsonStr(question), ttl, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("question cache write failed, id={}", questionId, e);
        }
        return question;
    }

    @Override
    public void evict(Long questionId) {
        if (questionId == null || questionId <= 0 || !cacheProperties.isEnabled()) {
            return;
        }
        try {
            redissonClient.getBucket(RedisConstant.getQuestionDetailKey(questionId)).delete();
            log.debug("question cache evicted, id={}", questionId);
        } catch (Exception e) {
            log.warn("question cache evict failed, id={}", questionId, e);
        }
    }
}
```

### 5.1 代码要点讲解


| 行 / 块                   | 作用              |
| ----------------------- | --------------- |
| `enabled` 判断            | 无 Redis 时降级查 DB |
| `try-catch` 读 Redis     | Redis 挂了不拖垮业务   |
| `#NULL#`                | 防穿透             |
| `ThreadLocalRandom` TTL | 防雪崩             |
| `log.debug` hit/miss    | 排错、压测对比         |


---

## 6. Step 5：QuestionService 暴露方法

`QuestionService.java` 增加：

```java
Question getQuestionFromCache(Long id);
void evictQuestionCache(Long id);
```

`QuestionServiceImpl.java`：

```java
private final QuestionCacheService questionCacheService;

@Override
public Question getQuestionFromCache(Long id) {
    return questionCacheService.getFromCache(id, this::getById);
}

@Override
public void evictQuestionCache(Long id) {
    questionCacheService.evict(id);
}
```

`getById` 是 MyBatis-Plus `ServiceImpl` 自带方法，会尊重逻辑删除。

---

## 7. Step 6：验证读路径（先不改 Controller）

临时在 `QuestionServiceImpl` 或单元测试里：

```java
Question q = questionService.getQuestionFromCache(2L);
```

然后：

```bash
redis-cli GET question:detail:2
redis-cli TTL question:detail:2
```

第二次调用应 **hit**，MySQL 不再查询（可开 SQL 日志验证）。

---

## 8. 常见编译问题


| 问题                          | 解决                                       |
| --------------------------- | ---------------------------------------- |
| 找不到 `JSONUtil`              | 项目已有 Hutool，检查 `pom.xml`                 |
| `QuestionCacheService` 循环依赖 | 使用 `QuestionLoader` 回调                   |
| Redis 连接失败                  | `cache.question.enabled=false` 或启动 Redis |


---

## 9. 本章自检

- [x] `RedisConstant` 已扩展  
- [x] `QuestionCacheServiceImpl` 已创建  
- [x] `getQuestionFromCache(2)` 后 Redis 有 key  
- [x] 再调一次，日志出现 `cache hit`  
- [x] `getQuestionFromCache(999999)` 后 Redis 有 `#NULL#`，TTL 约 60s  

下一章：**[07-代码实战二 写路径与Controller接入](./07-代码实战二-写路径与Controller接入.md)**