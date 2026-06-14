# 07 - 代码实战（二）：写路径与 Controller 接入

> 接上章读路径，完成**删缓存**、**接口接入**、**判题统一读缓存**。

---

## 1. 写路径：evict 已在第六章实现

更新 / 删除题目时调用 `questionService.evictQuestionCache(id)` 即可。

核心原则：**DB 操作成功后再 evict**。

---

## 2. Step 1：改造 QuestionController.getQuestionVO

文件：`controller/QuestionController.java`

**改前：**

```java
Question question = questionService.getById(id);
```

**改后：**

```java
Question question = questionService.getQuestionFromCache(id);
```

完整方法：

```java
@GetMapping("/get/vo")
public BaseResponse<QuestionVO> getQuestionVO(Long id) {
    ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
    Question question = questionService.getQuestionFromCache(id);
    ThrowUtils.throwIf(question == null, ErrorCode.NOT_FOUND_ERROR);
    QuestionVO questionVO = questionService.getQuestionVO(question);
    questionVO.setUserVO(userService.getUserVO(userService.getById(question.getUserId())));
    return ResultUtils.success(questionVO);
}
```

C 端 `customer_front_end` **无需改动**，仍请求同一 API。

---

## 3. Step 2：update 后删缓存

`QuestionController.updateQuestion` 在 `updateById` 成功后：

```java
boolean flag = questionService.updateById(question);
ThrowUtils.throwIf(!flag, ErrorCode.OPERATION_ERROR);
questionService.evictQuestionCache(question.getId());
return ResultUtils.success(true);
```

---

## 4. Step 3：delete 后删缓存

`QuestionController.deleteQuestion`：

```java
Long questionId = deleteRequest.getId();
// ... 校验、removeById ...
questionService.evictQuestionCache(questionId);
return ResultUtils.success(true);
```

逻辑删除后，缓存里若还有旧题，用户会继续看到已删题目 → **必须 evict**。

---

## 5. Step 4：判题接口统一读缓存

`QuestionServiceImpl.judgeQuestion`：

**改前：**

```java
Question question = this.getById(questionId);
```

**改后：**

```java
Question question = this.getQuestionFromCache(questionId);
```

否则管理员改答案后，判题仍用旧 `answer` 缓存（若曾误缓存）或绕过缓存逻辑不一致。

---

## 6. Step 5：add 题目要不要缓存？

**默认不需要**。新题第一次被访问时自然 miss → 加载 → 回填。

可选：运营导入后批量预热：

```java
public void warmUp(Long questionId) {
    Question q = getById(questionId);
    if (q != null) {
        questionCacheService.getFromCache(questionId, id -> q);
    }
}
```

---

## 7. 全链路验证清单

### 7.1 正常读

```bash
# 1. 清空
redis-cli DEL question:detail:2

# 2. 浏览器或 curl
curl "http://localhost:8101/api/question/get/vo?id=2" -H "token: 你的JWT"

# 3. 检查 Redis
redis-cli GET question:detail:2
redis-cli TTL question:detail:2   # 应在 1800~2100 之间
```

### 7.2 命中

同一请求再发一次，日志 `cache hit`，响应更快。

### 7.3 更新失效

1. 管理端修改题目 content
2. `redis-cli GET question:detail:2` → `(nil)`
3. 再访问 get/vo → 新 content，Redis 重新写入

### 7.4 穿透

```bash
curl "http://localhost:8101/api/question/get/vo?id=999999" -H "token: ..."
redis-cli GET question:detail:999999
# 应为 #NULL#
```

### 7.5 判题

提交客观题答案，确认用的是最新标准答案（改过题后测一次）。

---

## 8. 事务与 evict 顺序（进阶预告）

若在 `@Transactional` 方法里 update 后立刻 evict，事务未提交时另一个线程可能读 DB 旧数据并写回缓存。

更稳写法（第 ⑧ 课）：

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onQuestionUpdated(QuestionUpdatedEvent event) {
    questionCacheService.evict(event.getQuestionId());
}
```

Phase 1 可在 Controller 层 evict，题目更新频率低，一般够用。

---

## 9. 接入点汇总表


| 位置                                  | 改动                      |
| ----------------------------------- | ----------------------- |
| `QuestionController.getQuestionVO`  | `getQuestionFromCache`  |
| `QuestionController.updateQuestion` | 末尾 `evictQuestionCache` |
| `QuestionController.deleteQuestion` | 末尾 `evictQuestionCache` |
| `QuestionServiceImpl.judgeQuestion` | `getQuestionFromCache`  |
| 其他 `getById(questionId)` 读题         | 全局搜索，改为走缓存              |


**注意**：管理端分页列表仍走 `page` 查询，不要对列表里每题单独写 Redis（N+1 问题）。

---

## 10. 本章自检

- [x] get/vo 走缓存  
- [x] update/delete 会 evict  
- [x] judge 走缓存  
- [x] 七大验证场景至少做 4 个  

下一章：**[08-缓存一致性与分布式锁](./08-缓存一致性与分布式锁.md)**