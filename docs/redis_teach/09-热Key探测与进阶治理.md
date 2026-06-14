# 09 - 热 Key 探测与进阶治理

> 深入阅读：[Redis热Key探测调研](../Redis热Key探测调研.md)

---

## 1. 什么时候需要这一章

完成基础缓存后，若出现：

- 某一 `question:detail:{id}` QPS 极高  
- Redis 单节点 CPU 飙高  
- 热题缓存过期时 DB 抖一下  

就需要 **热 Key 探测 + 治理**，而不是再加 TTL。

---

## 2. 热 Key 是什么

**单个 key 访问频率远超其他 key**。

本项目典型原因：

- 模拟卷第一题全班同时打开  
- 错题榜 Top1 被反复点  
- 老师布置同一套作业  

---

## 3. 探测：应用层计数（推荐）

在 `QuestionCacheServiceImpl.getFromCache` 入口：

```java
hotKeyDetector.record(RedisConstant.getQuestionDetailKey(questionId));
```

`HotKeyDetector` 用 60 秒滑动窗口统计，超过阈值（如 500 次）标记为热 key。

多实例部署时用 Redisson 计数器汇总：`hotkey:counter:question:detail:2`。

详见调研文档 Phase 1。

---

## 4. 治理手段

| 手段 | 说明 |
|------|------|
| **Caffeine 本地 L1** | 最热 100 题放 JVM 内存，减 Redis 压力 |
| **互斥锁** | 第 ⑧ 课，防击穿 |
| **主动续期** | 热 key 快过期时后台刷新 TTL |
| **redis-cli --hotkeys** | 运维交叉验证（需 LFU 策略） |

### 4.1 二级缓存示意

```
请求 → Caffeine（L1）→ Redis（L2）→ MySQL
```

仅对 `isHot(key)==true` 的 key 走 L1，避免本地缓存过大。

---

## 5. 和题目业务结合

热题榜 ≈ 错题热度 + 访问统计，可给管理端「热门题目」运营位。

---

## 6. 本章可选实现

- [ ] `HotKeyDetector` + 定时打印 Top10  
- [ ] 热 key 启用 Caffeine  
- [ ] 管理端 `GET /admin/hotkey/top`（admin 权限）

基础缓存未完成前，**不必先做**本章。

下一章：**[10-验证排错与面试问答](./10-验证排错与面试问答.md)**
