# 04 - Cache-Aside（旁路缓存）模式

> 这是互联网项目里**用得最多**的缓存模式，本项目题目详情采用此模式。

---

## 1. 名字含义

- **Cache**：Redis 缓存  
- **Aside**：在一旁，**由业务代码主动管理**，不是数据库或框架自动代劳

对比：


| 模式                 | 谁管缓存            |
| ------------------ | --------------- |
| Cache-Aside        | 你的 Service 代码   |
| Read/Write Through | 缓存组件包一层，应用只调缓存层 |
| Write Behind       | 先写缓存，异步写 DB     |


面试默认问 Cache-Aside，答这个最稳。

---

## 2. 读路径（Read）

```
用户请求 GET /question/get/vo?id=2
         │
         ▼
┌────────────────────┐
│ getQuestionFromCache│
└─────────┬──────────┘
          │
    ┌─────▼─────┐
    │ Redis GET  │
    │ question:  │
    │ detail:2   │
    └─────┬─────┘
          │
     有值？├── 是 ──► 反序列化 Question ──► 返回（缓存命中 HIT）
          │
          否（MISS）
          │
          ▼
    ┌─────────────┐
    │ MySQL       │
    │ getById(2)  │
    └─────┬───────┘
          │
     存在？├── 否 ──► 写空值缓存 #NULL# ──► 返回 null
          │
          是
          │
          ▼
    写入 Redis（JSON + TTL）
          │
          ▼
       返回 Question
```

### 2.1 对应伪代码

```java
public Question getQuestionFromCache(Long id) {
    String key = RedisConstant.getQuestionDetailKey(id);
    RBucket<String> bucket = redissonClient.getBucket(key);
    String json = bucket.get();

    // 1. 空值占位
    if (RedisConstant.QUESTION_DETAIL_NULL_PLACEHOLDER.equals(json)) {
        return null;
    }
    // 2. 命中
    if (StrUtil.isNotBlank(json)) {
        return JSONUtil.toBean(json, Question.class);
    }
    // 3. Miss：查 DB
    Question question = this.getById(id);
    if (question == null) {
        bucket.set(RedisConstant.QUESTION_DETAIL_NULL_PLACEHOLDER,
                RedisConstant.QUESTION_DETAIL_NULL_TTL_SECONDS, TimeUnit.SECONDS);
        return null;
    }
    // 4. 回填
    long ttl = RedisConstant.QUESTION_DETAIL_TTL_SECONDS
            + ThreadLocalRandom.current().nextLong(0, RedisConstant.QUESTION_DETAIL_TTL_JITTER_SECONDS);
    bucket.set(JSONUtil.toJsonStr(question), ttl, TimeUnit.SECONDS);
    return question;
}
```

### 2.2 业务层之后

`QuestionController` 仍负责：

```java
Question question = questionService.getQuestionFromCache(id);
QuestionVO vo = questionService.getQuestionVO(question);
vo.setUserVO(userService.getUserVO(...));
```

缓存层**不知道** VO，只管 `Question`。

---

## 3. 写路径（Write / Update / Delete）

Cache-Aside 的写原则：**先更新数据库，再删除缓存**（不是更新缓存里的 JSON）。

```
管理员 POST /question/update
         │
         ▼
    updateById(question)     ← MySQL 权威数据已更新
         │
         ▼
    evictQuestionCache(id)   ← DEL question:detail:{id}
         │
         ▼
    下次读请求会 Miss，从 DB 加载最新数据
```

### 3.1 为什么「删」而不是「改」缓存


| 更新缓存                | 删除缓存    |
| ------------------- | ------- |
| 要写一遍完整 JSON 到 Redis | 一行 DEL  |
| 若 DB 事务回滚，缓存已脏      | 下次读自然回填 |
| 代码要和 DB 字段完全同步      | 逻辑简单    |


题目更新字段多（content、answer、options…），**删缓存更省心**。

### 3.2 新增题目

`addQuestion` 后 DB 有新 id，Redis 里本来就没有 key → **无需预热**，第一次访问自动加载。

---

## 4. 时序：读写并发时发生了什么

### 4.1 正常读（缓存命中）

```
线程A: GET Redis ──命中──► 返回（不访问 MySQL）
```

### 4.2 缓存未命中

```
线程A: GET Redis ──miss──► SELECT MySQL ──SET Redis──► 返回
```

### 4.3 更新题目

```
线程B: UPDATE MySQL ──DEL Redis
线程A: GET Redis ──miss──► SELECT MySQL（已是新数据）──SET Redis──► 返回新题
```

### 4.4 危险竞态（了解即可，第 ⑧ 课细讲）

```
线程B: UPDATE MySQL 进行中...
线程A: GET Redis miss ──SELECT 旧数据？──SET Redis 旧数据  ← 短暂脏读
线程B: DEL Redis
```

所以删缓存最好在 **DB 事务提交之后**；高要求场景用延迟双删。

---

## 5. Cache-Aside 在本项目的接入点


| 接口 / 方法                 | 读/写      | 缓存动作                   |
| ----------------------- | -------- | ---------------------- |
| `GET /question/get/vo`  | 读        | `getQuestionFromCache` |
| `judgeQuestion`         | 读 answer | `getQuestionFromCache` |
| `POST /question/update` | 写        | `evict`                |
| `POST /question/delete` | 写        | `evict`                |
| `POST /question/add`    | 写        | 无（可选预热）                |


**统一入口**：所有「按 id 读题目」必须走同一方法，否则会出现有的走缓存、有的直打 DB。

---

## 6. 与其他模式对比（面试扩展）


| 问题   | Cache-Aside | Read Through |
| ---- | ----------- | ------------ |
| 实现难度 | 低，显式代码      | 要封装缓存层       |
| 灵活性  | 高           | 中            |
| 业界使用 | 最广          | 部分中间件        |


---

## 7. 画图记忆（面试白板）

```
        ┌──────────┐
        │  应用    │
        └──┬───┬───┘
    读/写 │   │ 读 miss 时写回
          ▼   ▼
      ┌───────┐     ┌───────┐
      │ Redis │     │ MySQL │
      └───────┘     └───────┘
         写：只删缓存，不改 Redis 内容
```

---

## 8. 自测

1. Cache-Aside 读路径三步是什么？
2. 更新题目后为什ET 么删缓存而不是 S新 JSON？
3. `judgeQuestion` 为什么要和 `get/vo` 共用读缓存？

下一章：**[05-缓存三大问题穿透击穿雪崩](./05-缓存三大问题穿透击穿雪崩.md)**