# 03 - 缓存设计基础：Key、TTL 与序列化

---

## 1. Key 设计原则

Redis 的 key 是全局命名空间里的字符串，**没有表结构**，全靠命名约定。

### 1.1 推荐格式

```
业务:模块:维度:唯一标识
```

本项目题目详情：

```
question:detail:2
question:detail:105
```

签到（已有）：

```
user:signins:2026:1
```

### 1.2 为什么要这样写


| 好处    | 说明                                |
| ----- | --------------------------------- |
| 可读    | 一眼知道是什么业务                         |
| 可批量运维 | `SCAN question:detail:*` 统计题目缓存数量 |
| 避免冲突  | 不会和用户签到 key 撞车                    |


### 1.3 反例（不要这样）

```
q2           // 太短，无语义
cache_2      // 不知道缓存的什么
question2    // 和 question 表 id 混淆
```

### 1.4 代码里统一管理

扩展 `RedisConstant.java`（与签到并列）：

```java
public interface RedisConstant {

    String USER_SING_IN_REDIS_KEY_PREFIX = "user:signins";
    // ... 已有签到方法 ...

    /** 题目详情缓存 */
    String QUESTION_DETAIL_KEY_PREFIX = "question:detail";

    /** 空值占位：表示 DB 中不存在该题 */
    String QUESTION_DETAIL_NULL_PLACEHOLDER = "#NULL#";

    long QUESTION_DETAIL_TTL_SECONDS = 30 * 60L;
    long QUESTION_DETAIL_TTL_JITTER_SECONDS = 5 * 60L;
    long QUESTION_DETAIL_NULL_TTL_SECONDS = 60L;

    static String getQuestionDetailKey(long questionId) {
        return QUESTION_DETAIL_KEY_PREFIX + ":" + questionId;
    }
}
```

**规则**：业务代码里禁止手写 `"question:detail:" + id`，一律走 `getQuestionDetailKey`。

---

## 2. TTL（Time To Live）过期时间

### 2.1 为什么必须设过期

如果不设 TTL：

- 题目在 MySQL 里删了，Redis 里永远留着 → **脏数据**
- 内存只增不减 → Redis OOM

TTL = 到期自动删 key，是缓存的**安全网**（即使忘了手动 evict，最多错一段时间）。

### 2.2 本项目 TTL 策略


| 数据类型        | TTL               | 原因             |
| ----------- | ----------------- | -------------- |
| 正常题目        | 30 分钟 + 随机 0～5 分钟 | 防雪崩（见第 ⑤ 课）    |
| 不存在的 id（空值） | 60 秒              | 防穿透，且短一点避免长期占坑 |
| 签到 BitSet   | 通常不设或很长           | 一年一条，业务不同      |


### 2.3 代码写法

```java
long ttl = RedisConstant.QUESTION_DETAIL_TTL_SECONDS
        + ThreadLocalRandom.current().nextLong(0, RedisConstant.QUESTION_DETAIL_TTL_JITTER_SECONDS);

bucket.set(json, ttl, TimeUnit.SECONDS);
```

### 2.4 随机抖动解释

若 1000 道题都在 14:00 导入，TTL 都是 1800 秒，则 14:30 **同时过期** → 雪崩。  
每道题 TTL 略不同，过期时间散开。

---

## 3. 序列化：Java 对象 ↔ Redis 字符串

Redis 只存字节/字符串，不能直接存 `Question` 对象。

### 3.1 本项目选择：JSON 字符串

```java
// 写入
String json = JSONUtil.toJsonStr(question);
bucket.set(json, ttl, TimeUnit.SECONDS);

// 读取
String json = bucket.get();
Question question = JSONUtil.toBean(json, Question.class);
```

项目已有 Hutool，也可用 Jackson `ObjectMapper`，**全项目统一一种**。

### 3.2 为什么选 JSON 而不是 Java 序列化


| 方式               | 优点         | 缺点         |
| ---------------- | ---------- | ---------- |
| JSON             | 可读、跨语言、易调试 | 体积略大       |
| JDK Serializable | 省事         | 不可读、类改字段易挂 |
| Protobuf         | 省空间        | 题目场景过度设计   |


`redis-cli GET question:detail:2` 能看到人类可读的 JSON，排错友好。

### 3.3 Date 字段注意

`Question` 有 `createTime`、`updateTime`（`java.util.Date`）。  
序列化/反序列化时确保时区一致（Jackson 配 `yyyy-MM-dd HH:mm:ss` 或 ISO-8601）。

### 3.4 空值占位

DB 查不到时，不要不缓存（会穿透），也不要缓存 `null` 字符串歧义：

```java
bucket.set(RedisConstant.QUESTION_DETAIL_NULL_PLACEHOLDER, 60, TimeUnit.SECONDS);
```

读时：

```java
if (RedisConstant.QUESTION_DETAIL_NULL_PLACEHOLDER.equals(json)) {
    return null;
}
```

---

## 4. 缓存什么对象：Question 还是 QuestionVO

### 4.1 当前接口返回结构

`QuestionVO` = `Question` 字段 + `UserVO userVO`（创建者昵称头像）

### 4.2 推荐缓存 `Question` 实体


| 缓存 Question           | 缓存完整 QuestionVO          |
| --------------------- | ------------------------ |
| 与 DB 一行对应             | 含 userVO，用户改昵称后 VO 过期    |
| 更新题目只 evict 一个 key    | 要同时考虑用户变更                |
| Controller 里再查一次 user | 少一次 user 查询（但 user 变更麻烦） |


**结论**：缓存 `Question`，`userVO` 仍每次查库（或以后单独做 user 缓存）。

### 4.3 不要缓存进题目 key 的数据


| 数据                     | 原因     |
| ---------------------- | ------ |
| `user_question_status` | 每个用户不同 |
| `question_favorite`    | 每个用户不同 |
| 判题结果                   | 实时状态   |


这些保持独立 API，避免 key 变成 `question:detail:2:user:5` 爆炸。

---

## 5. 缓存容量粗算（心里有数）

假设一题 JSON 约 5KB，1000 题全缓存 ≈ 5MB，单机 Redis 完全够用。  
面试时被问「会不会撑爆内存」：题目量级千～万级，String 缓存可控；真正要担心的是**热 key 访问频率**，不是体积。

---

## 6. 设计检查清单

实现前自问：

- [ ] Key 是否有统一前缀和生成方法？  
- [ ] 是否设置了 TTL？  
- [ ] 不存在的 id 是否有短 TTL 空值？  
- [ ] 序列化格式是否统一 JSON？  
- [ ] 缓存对象是否与 DB 行一一对应？  
- [ ] 是否把用户态数据误塞进题目 key？

---

## 7. 自测

1. `question:detail:2` 和 `user:signins:2026:1` 各存的是什业务数么据？
2. 正常题目 TTL 为什么比空值 TTL 长？
3. 为什么缓存 `Question` 而不是 `QuestionVO`？

下一章：**[04-Cache-Aside旁路缓存模式](./04-Cache-Aside旁路缓存模式.md)**