# 题目详情 Redis 缓存方案

> 面向本项目 `interview_copilot` 的落地设计文档。  
> 场景：**C 端做题页高频调用** `GET /question/get/vo?id=`，同一道题会被反复读取，适合用 Redis 做读缓存。  
> **零基础教程**：[redis_teach 系列](./redis_teach/00-学习路线图.md)（从 Redis 概念到完整代码分步实现）

---

## 1. 为什么要做

### 1.1 业务场景


| 场景        | 行为                       | 读压力          |
| --------- | ------------------------ | ------------ |
| 做题页       | 进入 `/question/:id`，拉题目详情 | 每题至少 1 次     |
| 顺序刷题      | 上一题 / 下一题切换              | 短时间内连续读多题    |
| 错题重做 / 收藏 | 单题进入                     | 同一热门题被多人反复访问 |


题目表 容易成为**热点读**。`question` 单条记录字段多（题干 LaTeX、选项 JSON、答案、解析），一次查询 + 关联创建者信息，在并发下

### 1.2 当前代码路径（无缓存）

```
QuestionController.getQuestionVO(id)
  → questionService.getById(id)          // MySQL
  → questionService.getQuestionVO(...)   // 内存 Bean 拷贝
  → userService.getById + getUserVO      // 又一次 MySQL
```

瓶颈在 **2 次 DB 查询**，且做题是典型「读多写少」。

### 1.3 预期收益

- 热点题 P99 延迟从毫秒级 DB 降到亚毫秒级 Redis
- 降低 MySQL 连接与 InnoDB 缓冲池压力
- 面试可讲：**Cache-Aside + 三大经典问题（穿透/击穿/雪崩）+ 缓存一致性**

---

## 2. 缓存什么、不缓存什么

### 2.1 推荐：缓存 `Question` 实体，不缓存完整 `QuestionVO`


| 方案                        | 优点                | 缺点                |
| ------------------------- | ----------------- | ----------------- |
| 缓存 `QuestionVO`（含 userVO） | 接口层直接返回           | 创建者改名后 VO 过期；体积更大 |
| **缓存 `Question` 实体** ✅    | 与 DB 行一一对应；失效边界清晰 | 仍需查一次用户（可二级优化）    |
| 只缓存 id 列表                 | 极简                | 对详情接口帮助有限         |


**结论**：第一版缓存 `**Question` JSON**，Controller 层照旧组装 `userVO`。用户头像/昵称变更频率远低于题目被读频率，可接受。

### 2.2 不纳入缓存的接口（第一版）


| 接口                            | 原因                         |
| ----------------------------- | -------------------------- |
| `POST /question/list/page/vo` | 条件组合多，key 难设计，收益低于详情       |
| `POST /question/judge`        | 必须读最新 `answer`，走缓存需与更新严格失效 |
| 知识点筛题分页                       | 动态 ID 集合，适合后续「题库维度」缓存      |


判题接口 `judgeQuestion` 内部也调用了 `getById`，**实现缓存后应统一走 `getQuestionFromCache(id)`**，避免绕过缓存。

### 2.3 与用户态数据分离

以下数据**不要**放进题目详情缓存：

- `user_question_status`（做过没有、对错）
- `question_favorite`（是否收藏）

它们是「用户 × 题目」维度，应用独立接口查询（你项目已这样做），避免缓存 key 爆炸。

---

## 3. 总体架构：Cache-Aside（旁路缓存）

```
读请求
  │
  ├─ 1. 查 Redis  key = question:detail:{id}
  │      ├─ 命中 → 反序列化 Question → 返回
  │      └─ 未命中 ↓
  │
  ├─ 2. 查 MySQL getById(id)
  │      ├─ 不存在 → 写空值缓存（防穿透）→ 抛 NOT_FOUND
  │      └─ 存在 → 写入 Redis（带 TTL）→ 返回
  │
  └─ 3. Controller 组装 QuestionVO + UserVO

写请求（update / delete）
  │
  ├─ 1. 更新 MySQL（已有逻辑）
  └─ 2. 删除 Redis  key = question:detail:{id}   （先更新 DB，再删缓存）
```

这是最常见、面试最好讲的模式：**先更新数据库，再删除缓存**（延迟双删可作为进阶）。

---

## 4. Key 与常量设计

在现有 `RedisConstant` 旁扩展（与签到 key 风格一致）：

```java
// constant/RedisConstant.java
String QUESTION_DETAIL_KEY_PREFIX = "question:detail";

static String getQuestionDetailKey(long questionId) {
    return QUESTION_DETAIL_KEY_PREFIX + ":" + questionId;
}

/** 空值占位，表示题目不存在 */
String QUESTION_DETAIL_NULL_PLACEHOLDER = "#NULL#";

/** 基础过期时间：30 分钟 */
long QUESTION_DETAIL_TTL_SECONDS = 30 * 60L;

/** 随机抖动上限：5 分钟，防雪崩 */
long QUESTION_DETAIL_TTL_JITTER_SECONDS = 5 * 60L;
```

命名规范：`业务:模块:维度:id`，方便 Redis 运维按前缀统计、清理。

---

## 5. 核心流程详解

### 5.1 读取流程

```java
public Question getQuestionFromCache(Long id) {
    String key = RedisConstant.getQuestionDetailKey(id);
    String json = bucket.get(); // Redisson RBucket<String>

    if (QUESTION_DETAIL_NULL_PLACEHOLDER.equals(json)) {
        return null; // 空值缓存，避免穿透
    }
    if (StrUtil.isNotBlank(json)) {
        return JSON.parseObject(json, Question.class);
    }

    // 缓存未命中 —— 可选：互斥锁防击穿（见 6.2）
    Question question = this.getById(id);

    if (question == null) {
        // 空值缓存，较短 TTL
        bucket.set(PLACEHOLDER, 60, TimeUnit.SECONDS);
        return null;
    }

    long ttl = BASE_TTL + ThreadLocalRandom.current().nextLong(JITTER);
    bucket.set(JSON.toJSONString(question), ttl, TimeUnit.SECONDS);
    return question;
}
```

**注意**：MyBatis-Plus 逻辑删除字段 `isDelete`，`getById` 默认会过滤已删记录，与缓存语义一致。

### 5.2 写入 / 失效流程

凡修改 `question` 表数据的操作，**必须在事务提交后删除缓存**：


| 操作   | 代码位置                                               | 失效动作                     |
| ---- | -------------------------------------------------- | ------------------------ |
| 更新题目 | `QuestionController.updateQuestion` → `updateById` | `evictQuestionCache(id)` |
| 删除题目 | `QuestionController.deleteQuestion` → `removeById` | `evictQuestionCache(id)` |
| 新增题目 | `addQuestion`                                      | 无需预热（首次访问再加载）            |


```java
public void evictQuestionCache(Long questionId) {
    if (questionId == null || questionId <= 0) return;
    redissonClient.getBucket(RedisConstant.getQuestionDetailKey(questionId)).delete();
}
```

### 5.3 Controller 改造示意

```java
@GetMapping("/get/vo")
public BaseResponse<QuestionVO> getQuestionVO(Long id) {
    ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
    // 原：Question question = questionService.getById(id);
    Question question = questionService.getQuestionFromCache(id);
    ThrowUtils.throwIf(question == null, ErrorCode.NOT_FOUND_ERROR);
    QuestionVO questionVO = questionService.getQuestionVO(question);
    questionVO.setUserVO(userService.getUserVO(userService.getById(question.getUserId())));
    return ResultUtils.success(questionVO);
}
```

`judgeQuestion` 内同样改为 `getQuestionFromCache`。

---

## 6. 三大问题与对策

### 6.1 缓存穿透（查不存在的数据）

**场景**：恶意传 `id=999999999`，缓存和 DB 都没有，每次打到 DB。

**方案**：

1. **接口层校验**：`id <= 0` 直接拒绝（已有）
2. **空值缓存**：DB 查不到时，Redis 写入占位符 `#NULL#`，TTL **60 秒**（短于正常缓存）
3. 进阶：布隆过滤器记录已存在题目 id（题量大时再上）

### 6.2 缓存击穿（热点 key 过期瞬间）

**场景**：某道真题访问量极大，缓存刚好过期，大量线程同时查 DB。

**方案（按复杂度选择）**：


| 级别  | 做法                        | 适用         |
| --- | ------------------------- | ---------- |
| MVP | 仅 TTL + 随机抖动              | 题库规模小、面试够用 |
| 推荐  | **互斥锁**（Redisson `RLock`） | 热点题明显时     |
| 进阶  | 逻辑过期（值里带过期时间，异步刷新）        | 超高并发       |


互斥锁伪代码：

```java
String lockKey = "lock:question:detail:" + id;
RLock lock = redissonClient.getLock(lockKey);
try {
    if (lock.tryLock(3, 10, TimeUnit.SECONDS)) {
        // 双重检查：再读一次缓存
        Question cached = readFromRedis(id);
        if (cached != null) return cached;
        // 查 DB 并回填
    }
} finally {
    if (lock.isHeldByCurrentThread()) lock.unlock();
}
```

### 6.3 缓存雪崩（大量 key 同时过期）

**场景**：30 分钟 TTL 的 key 在同一时刻批量写入，同时失效。

**方案**：

```java
long ttl = 1800 + ThreadLocalRandom.current().nextLong(0, 300); // 30~35 分钟
```

题库批量导入后可选：**错峰预热**（见第 8 节）。

---

## 7. 缓存一致性

### 7.1 本项目推荐：先写 DB，再删缓存

```
updateQuestion()
  1. updateById(question)     // 事务内
  2. evictQuestionCache(id) // 事务提交后执行更稳
```

若删缓存失败：最多脏读旧数据直到 TTL 过期，**可接受**（题目更新频率低）。

### 7.2 延迟双删（可选进阶）

对一致性要求更高时：

```
1. 删缓存
2. 更新 DB
3. sleep 500ms（或使用 MQ 延迟消息）
4. 再删一次缓存
```

面试时说明：**为什么 sleep**——避免「读请求在写 DB 完成前又把旧值写入缓存」的竞态。

### 7.3 与 ES 增量同步的关系

题目变更后：

- Redis：立即 `evict`（强一致读路径）
- ES：`IncSyncQuestionToEs` 分钟级同步（搜索路径最终一致）

两套索引各管各的，不冲突。

---

## 8. 代码结构建议

新增类，避免 `QuestionServiceImpl` 过于臃肿：

```
constant/
  RedisConstant.java          // 扩展 question detail key

service/
  QuestionCacheService.java   // 接口
  impl/
    QuestionCacheServiceImpl.java  // get / put / evict

service/impl/
  QuestionServiceImpl.java    // 委托 QuestionCacheService
```

`QuestionCacheService` 职责：


| 方法                                      | 说明          |
| --------------------------------------- | ----------- |
| `Question getFromCache(Long id)`        | 读穿透 + 回填    |
| `void putCache(Question question)`      | 手动预热        |
| `void evict(Long id)`                   | 失效          |
| `void evictBatch(Collection<Long> ids)` | 批量导入后清理（可选） |


序列化：项目已有 JSON 生态，用 **Jackson 或 Hutool JSON** 与 `Question` 互转；`Date` 字段注意时区格式统一。

---

## 9. 分步落地清单

### Phase 1：最小可用（面试可演示）

- [ ] `RedisConstant` 增加题目 key
- [ ] 实现 `QuestionCacheServiceImpl`（Cache-Aside + TTL 抖动 + 空值缓存）
- [ ] `getQuestionVO` 接口改用 `getQuestionFromCache`
- [ ] `update` / `delete` 后调用 `evict`
- [ ] `judgeQuestion` 改用缓存读

### Phase 2：加固

- [ ] 热点 key 互斥锁
- [ ] `@TransactionalEventListener(phase = AFTER_COMMIT)` 在事务提交后删缓存
- [ ] 日志：cache hit / miss 指标（便于压测对比）

### Phase 3：扩展（可选）

- [ ] 创建者 `UserVO` 二级缓存 `user:vo:{userId}`
- [ ] 管理端批量导题后异步预热 Top N 题目
- [ ] Redis + 本地 Caffeine 二级缓存（单机 QPS 极高时）

---

## 10. 验证方式

### 10.1 功能

1. 首次访问 `GET /question/get/vo?id=2` → Redis 出现 `question:detail:2`
2. 再次访问 → 日志标记 hit，MySQL 慢查询无新增
3. 管理端 `update` 题目 → Redis key 被删除
4. 再访问 → miss 后加载新内容
5. 访问不存在 id → 短 TTL 空值 key，连续请求不打穿 DB

### 10.2 Redis CLI

```bash
redis-cli
GET question:detail:2
TTL question:detail:2
DEL question:detail:2   # 模拟失效
```

### 10.3 压测对比（面试加分）

用 ab / JMeter 对 `/question/get/vo?id=2` 打 1000 并发：


| 指标        | 无缓存   | 有缓存     |
| --------- | ----- | ------- |
| P99 延迟    | 较高    | 明显下降    |
| MySQL QPS | ≈ 请求数 | ≈ 1（首次） |


---

## 11. 面试话术模板（3 分钟版）

> **背景**：做题页会高频读题目详情，题干含 LaTeX 字段较大，MySQL 热点读明显。  
> **方案**：采用 Cache-Aside，Redis 存 `Question` 实体 JSON，key 为 `question:detail:{id}`，TTL 30 分钟加随机抖动防雪崩。  
> **穿透**：不存在的 id 缓存空值占位 60 秒。  
> **击穿**：热点题过期时用 Redisson 分布式锁，只放一个线程回源 DB。  
> **一致性**：题目 update/delete 后删缓存；与用户做题状态、收藏分离，避免 key 维度爆炸。  
> **权衡**：列表页和 ES 搜索不走这套缓存，详情读是主要收益点。

---

## 12. 常见坑（本项目特别注意）

1. **逻辑删除**：`removeById` 后必须 `evict`，否则缓存里仍是旧题。
2. **判题接口**：必须用同一套 `getQuestionFromCache`，否则更新答案后判题仍用旧答案。
3. **不要把 answer 单独缓存一份**：一道题一个 key 即可，减少一致性问题。
4. **Redisson 与 Spring Data Redis**：项目已用 Redisson（签到 BitSet），题目缓存继续用 `RBucket<String>`，无需再引 RedisTemplate。
5. **本地开发**：`application.yml` 中 Redis 需可用；不可用时应降级为直接查 DB（可加 `try-catch` 或配置开关 `cache.question.enabled`）。

---

## 13. 配置开关（建议）

```yaml
# application.yml
cache:
  question:
    enabled: true
    ttl-seconds: 1800
    null-ttl-seconds: 60
```

```java
@Value("${cache.question.enabled:true}")
private boolean questionCacheEnabled;

public Question getQuestionFromCache(Long id) {
    if (!questionCacheEnabled) {
        return this.getById(id);
    }
    // ... 走 Redis
}
```

方便本地无 Redis 时开发、压测时对比开关效果。

---

## 14. 与现有技术栈的衔接


| 已有能力                                | 本方案用法             |
| ----------------------------------- | ----------------- |
| `RedissonClient` + `RedissonConfig` | `RBucket` 存 JSON  |
| `RedisConstant`                     | 统一 key 前缀         |
| MyBatis-Plus 逻辑删除                   | `getById` 与缓存语义一致 |
| `QuestionController`                | 读改删三个入口挂接         |
| C 端 `QuestionPracticePage`          | 无感，仍调同一 API       |


---

## 15. 总结


| 项    | 选择                                 |
| ---- | ---------------------------------- |
| 模式   | Cache-Aside                        |
| 缓存对象 | `Question` 实体（JSON）                |
| 客户端  | Redisson `RBucket`                 |
| Key  | `question:detail:{id}`             |
| TTL  | 30min + 0~5min 随机                  |
| 穿透   | 空值短缓存                              |
| 击穿   | Redisson 锁（Phase 2）                |
| 一致性  | 写后删缓存                              |
| 改造面  | `get/vo`、`judge`、`update`、`delete` |


按 **Phase 1** 实现即可在面试中完整讲述；Phase 2/3 作为「如果流量上来我会怎么演进」的加分项。