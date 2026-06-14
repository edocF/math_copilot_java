# Redis 热 Key 探测调研

> 面向 `interview_copilot` 数学刷题平台。  
> 前置文档：[题目详情 Redis 缓存方案](./题目详情Redis缓存方案.md)  
> 目标：在引入 `question:detail:{id}` 缓存后，**如何发现、度量、治理**访问集中的热 Key。

---

## 1. 概念与业务背景

### 1.1 什么是热 Key（Hot Key）

在某一时间窗口内，**访问频率或 QPS 远高于平均水平**的 Redis Key。

与本项目相关的典型热 Key：


| Key 模式                         | 产生原因                             |
| ------------------------------ | -------------------------------- |
| `question:detail:{id}`         | 某道题为模拟卷首题、首页推荐、错题榜 Top1，大量用户同时打开 |
| `question:detail:{id}`         | 考试/班级统一练习，老师布置同一套题               |
| `user:signins:{year}:{userId}` | 签到接口在固定时段集中触发（相对分散，热 Key 程度较低）   |


### 1.2 热 Key vs 大 Key（Big Key）


| 维度    | 热 Key                | 大 Key                          |
| ----- | -------------------- | ------------------------------ |
| 问题本质  | **读/写次数多**           | **单 Key 体积大**（如超大 JSON、长 List） |
| 典型影响  | 单分片 CPU 飙高、带宽打满、缓存击穿 | 阻塞 Redis 单线程、慢查询、迁移困难          |
| 本题库场景 | **更常见**（热门题被反复读）     | 较少（单题 JSON 通常 KB 级）            |


本文聚焦 **热 Key 探测**；大 Key 可用 `redis-cli --bigkeys` 或 `MEMORY USAGE` 另做巡检。

### 1.3 不做探测会怎样

热 Key 落在 Redis **单机或 Cluster 某一 Slot** 上时：

1. 该节点 CPU / 网络成为瓶颈，整体 P99 抖动
2. 缓存过期瞬间触发**击穿**，DB 压力陡增
3. 无法做「针对性优化」（本地二级缓存、提前续期、副本扩散读）

因此：**缓存方案与热 Key 治理应配套设计**，而不是上了 Redis 就结束。

---

## 2. 本项目是否需要热 Key 探测

### 2.1 当前阶段判断


| 因素   | 现状                        | 结论                      |
| ---- | ------------------------- | ----------------------- |
| 部署形态 | 单机 Redis + Spring Boot    | 无天然分片，热 Key 直接打满单实例     |
| 数据规模 | 题目百～千级（种子数据 10 题，可扩展）     | 全量 Key 不多，但**访问分布极不均匀** |
| 读路径  | `GET /question/get/vo` 高频 | 符合热 Key 典型场景            |
| 团队规模 | 个人/小团队面试项目                | 不宜上过重基建                 |


**结论**：

- **MVP / 面试演示**：应用层轻量探测（滑动窗口 TopN）即可，成本低、故事完整
- **若上线有真实流量**：叠加 Redis 内置采样 + 告警
- **百万 QPS 级**：才需要考虑 Proxy、读写分离、热 Key 副本等重型方案

### 2.2 与「题目详情缓存」的关系

```
                    ┌─────────────────┐
  读请求 ──────────► │ question:detail │ ◄── 正常 Key：分散在不同 id
                    └─────────────────┘
                              │
                    热 Key：某一 id  QPS 异常高
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
        应用层探测        监控告警         治理手段
        (本调研重点)    (Redis/ Prometheus) (本地缓存/续期)
```

---

## 3. 探测方案总览

### 3.1 方案对比表


| 方案                                | 原理                                 | 优点                           | 缺点                                            | 适合本项目     |
| --------------------------------- | ---------------------------------- | ---------------------------- | --------------------------------------------- | --------- |
| **A. 应用层计数**                      | 在 `getFromCache` 入口对 key 做滑动窗口统计   | 精准对应业务 Key；无侵入 Redis；可联动自动治理 | 多实例需汇总；有少量 CPU 开销                             | ⭐⭐⭐ 首选    |
| **B. Redis `--hotkeys`**          | Redis 4.0+ LFU 采样统计                | 官方、零代码                       | 需 `maxmemory-policy` 含 LFU；采样有延迟；key 名不直观时需对照 | ⭐⭐ 辅助     |
| **C. `MONITOR` 命令**               | 实时打印所有命令                           | 实现简单                         | **严禁生产常用**；性能毁灭级                              | ❌ 仅本地调试   |
| **D. Redis 慢查询 + latency**        | `SLOWLOG`、`--latency`              | 发现慢命令                        | 不能直接给出「最热 key 排行」                             | ⭐ 辅助      |
| **E. 代理层统计**                      | Twemproxy、Codis、阿里云 Proxy 热 Key 分析 | 对业务透明、全量统计                   | 需额外组件；单机 Redis 用不上                            | ❌ 现阶段过重   |
| **F. 客户端 SDK（Jedis/ Lettuce 钩子）** | 拦截每次 Redis 命令打点                    | 与 A 类似                       | 需封装所有 Redis 访问路径                              | ⭐⭐        |
| **G. 日志采样 + ELK**                 | 采样打印 access key，离线聚合               | 与现有日志栈结合                     | 实时性差；搭建成本高                                    | ⭐ 有 ELK 时 |
| **H. OpenTelemetry / Micrometer** | 指标 `redis.key.access` 带 tag        | 可接 Grafana 告警                | 高基数 tag（每个 id 一个 series）需**聚合 TopN** 而非全量     | ⭐⭐ 进阶     |


### 3.2 推荐组合（本项目）

```
Phase 1（面试可讲）  应用层 HotKeyDetector + 定时日志 Top10
Phase 2（有流量）    Redis CONFIG + --hotkeys 巡检 + 告警
Phase 3（演进）      热 Key 自动本地缓存 / 动态续期
```

---

## 4. 方案 A：应用层热 Key 探测（推荐落地）

### 4.1 思路

在 `QuestionCacheService.getFromCache(id)` **入口处**，对逻辑 Key `question:detail:{id}` 递增计数器；用**滑动时间窗口**（如最近 60 秒）统计 QPS，超过阈值则标记为热 Key。

与签到 BitSet、题目缓存共用 Redisson，但统计部分可用：

- **本地**：`ConcurrentHashMap` + 滑动窗口（单实例够用）
- **分布式**：`RMapCache` / `RAtomicLong` + TTL（多实例汇总）

### 4.2 单实例滑动窗口（示意）

```java
/**
 * 简化示意：固定窗口计数（生产可用 Caffeine + 定时重置，或环形桶滑动窗口）
 */
public class HotKeyDetector {

    private static final long WINDOW_MS = 60_000;
    private static final long HOT_THRESHOLD = 500; // 60s 内 500 次视为热 key

    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public void record(String key) {
        counters.computeIfAbsent(key, k -> new WindowCounter(WINDOW_MS))
                .increment();
    }

    public boolean isHot(String key) {
        WindowCounter c = counters.get(key);
        return c != null && c.countInWindow() >= HOT_THRESHOLD;
    }

    public List<HotKeyEntry> topN(int n) {
        return counters.entrySet().stream()
                .map(e -> new HotKeyEntry(e.getKey(), e.getValue().countInWindow()))
                .sorted(Comparator.comparingLong(HotKeyEntry::getCount).reversed())
                .limit(n)
                .collect(Collectors.toList());
    }
}
```

挂接点：

```java
public Question getFromCache(Long id) {
    String key = RedisConstant.getQuestionDetailKey(id);
    hotKeyDetector.record(key);           // 探测
    if (hotKeyDetector.isHot(key)) {
        // 可选：走本地 Caffeine 二级缓存
    }
    // ... 原有 Cache-Aside
}
```

### 4.3 多实例汇总（Redisson）

单机部署可跳过；若水平扩展多个 Spring Boot 节点：

```
实例1 ──incr──► Redis: hotkey:counter:question:detail:2  (TTL 60s)
实例2 ──incr──► 同一 key
                      │
                      ▼
              定时任务扫描 hotkey:counter:* 取 TopN
```

Key 设计：

```
hotkey:counter:{businessKey}   →  String 计数，TTL = 窗口长度
hotkey:mark:{businessKey}      →  已标记为热 key，便于治理逻辑读取
```

### 4.4 阈值如何定


| 方法   | 说明                                          |
| ---- | ------------------------------------------- |
| 静态阈值 | 如 60s 内 > 500 次；适合面试项目、易解释                  |
| 动态基线 | 全站 QPS 均值 × 10；需先采集再调参                      |
| 分业务  | `question:detail:*` 与 `user:signins:*` 分开配置 |


初期建议：**静态阈值 + 管理端/日志输出 Top10**，压测后调整。

### 4.5 优缺点


| 优点                             | 缺点                |
| ------------------------------ | ----------------- |
| 与业务 Key 语义一致（直接是 `questionId`） | 只能统计「经过这段代码」的访问   |
| 不依赖 Redis 版本与内存策略              | 多实例要额外汇总          |
| 可探测后**立即触发治理**（本地缓存、续期）        | 极端高 QPS 下计数本身需轻量化 |


---

## 5. 方案 B：Redis 内置 `--hotkeys`

### 5.1 原理

Redis 4.0 起，在 `maxmemory-policy` 为 **LFU 系列**（`volatile-lfu` / `allkeys-lfu`）时，Redis 内部会对 Key 访问频率采样，可通过：

```bash
redis-cli --hotkeys
```

输出一段时间内访问最频繁的 Key 列表（采样结果，非精确值）。

### 5.2 前置条件

```conf
maxmemory 256mb
maxmemory-policy allkeys-lfu   # 或 volatile-lfu
```

**注意**：若使用 `noeviction` 或 LRU 策略，热 Key 统计能力会打折扣或不可用。

### 5.3 使用方式

```bash
# 持续采样一段时间（期间需有真实流量）
redis-cli -h localhost -p 6379 --hotkeys

# 示例输出
# Sampled 5000 keys. Hot keys' stats:
# key 'question:detail:2' printed 1200 times (24.00%)
# key 'question:detail:7' printed 800 times (16.00%)
```

### 5.4 适用场景

- **运维巡检**、压测后验证
- 与业务代码解耦

### 5.5 局限


| 局限   | 说明                                 |
| ---- | ---------------------------------- |
| 非实时  | 需采样窗口，突发流量可能滞后                     |
| 策略耦合 | 必须 LFU 淘汰策略，可能与原有 `maxmemory` 规划冲突 |
| 全库混合 | 签到、题目、锁 key 混在一起，需按前缀过滤            |
| 生产频率 | 不宜 7×24 常驻执行，建议定时离线跑               |


**本项目定位**：作为应用层探测的**交叉验证**，而非唯一手段。

---

## 6. 方案 C～H 简述

### 6.1 MONITOR（不推荐生产）

```bash
redis-cli MONITOR
```

实时流式输出所有命令，可肉眼看热点 key，但会严重拖慢 Redis。**仅本地开发调试**。

### 6.2 慢查询与 Latency

```bash
redis-cli SLOWLOG GET 10
redis-cli --latency
redis-cli --latency-history
```

发现「某类命令变慢」，但**不能直接给出热 Key 排行榜**。

### 6.3 代理 / 云厂商

- **Twemproxy / Codis**：代理层统计
- **阿里云 Redis 热 Key 分析**、**腾讯云 热 Key 查询**：控制台可视化

适合集群版、有预算的生产环境；个人项目面试中可作为「上线后选型」提及。

### 6.4 日志 + 离线分析

访问层采样日志：

```json
{"key":"question:detail:2","op":"GET","ts":1710000000}
```

Fluentd / Filebeat → ES → Kibana 聚合。实时性差，适合事后复盘。

### 6.5 Micrometer 指标（注意高基数）

```java
// 反模式：每个 id 一个 metric tag → 时间序列爆炸
// counter.increment("redis.key.access", "key", "question:detail:2");

// 正做法：只 export TopN 或超过阈值的 key
if (count > threshold) {
    meterRegistry.counter("redis.hotkey.hit", "key", logicalKey).increment();
}
```

---

## 7. 探测到热 Key 之后怎么办（治理）

探测不是目的，**治理**才是。常见手段：


| 手段                      | 原理                                     | 本项目可行性                 |
| ----------------------- | -------------------------------------- | ---------------------- |
| **本地二级缓存（L1）**          | JVM Caffeine 缓存最热少数 Key，毫秒级、减 Redis 压力 | ⭐⭐⭐ 热 Key 少时极有效        |
| **逻辑过期 / 主动续期**         | 热 Key 快过期时异步刷新，避免击穿                    | ⭐⭐⭐ 与题目详情方案 Phase 2 一致 |
| **互斥锁回源**               | 过期时仅一线程查 DB                            | ⭐⭐⭐ 已在缓存方案中            |
| **热 Key 副本（Redis 读扩散）** | 将 `key` 复制为 `key:{1..n}`，读时随机选一个       | ⭐ Cluster 多分片时才有意义     |
| **提前预热**                | 活动前加载模拟卷题目                             | ⭐⭐ 运营已知场景              |
| **降级**                  | 极端情况返回静态页 / 简化题干                       | ⭐ 保底                   |


推荐治理链路：

```
record(key) → isHot(key)? → 启用 Caffeine L1 + 延长 TTL + 互斥锁
                │
                └─ 定时 topN() → 日志 / 管理端「热题榜」→ 运营配置推荐位
```

与业务结合：**热题榜 ≈ 错题热度榜的读侧镜像**，可复用同一套统计。

---

## 8. 本项目推荐架构

```
┌──────────────────────────────────────────────────────────────┐
│                     QuestionCacheService                      │
│  getFromCache(id)                                             │
│    1. hotKeyDetector.record(key)                              │
│    2. if isHot(key) → localCache.get(key)  // Caffeine L1   │
│    3. else → Redisson RBucket  // 原有 L2                     │
│    4. miss → MySQL → 回填                                    │
└──────────────────────────────────────────────────────────────┘
         │                              │
         ▼                              ▼
  HotKeyDetector                  Redis (Redisson)
  (内存 / Redisson 计数)           question:detail:{id}
         │
         ▼
  @Scheduled 每 60s
  log Top10 / 写入 hot_key_snapshot 表（可选）
```

### 8.1 建议新增模块

```
service/
  HotKeyDetector.java          // 接口：record / isHot / topN
  impl/
    LocalHotKeyDetector.java   // 单实例滑动窗口
    RedisHotKeyDetector.java   // 多实例（可选）

config/
  HotKeyProperties.java        // threshold, windowSeconds, enabled

job/
  HotKeyReportJob.java           // 定时输出 TopN
```

### 8.2 配置示例

```yaml
hotkey:
  enabled: true
  window-seconds: 60
  threshold: 500          # 窗口内访问次数
  report-top-n: 10
  local-cache:
    enabled: true
    max-size: 100         # 仅缓存最热 100 个 key
    ttl-seconds: 30
```

### 8.3 与现有 `RedisConstant` 扩展

```java
String HOT_KEY_COUNTER_PREFIX = "hotkey:counter";
String HOT_KEY_MARK_PREFIX = "hotkey:mark";

static String getHotKeyCounterKey(String businessKey) {
    return HOT_KEY_COUNTER_PREFIX + ":" + businessKey;
}
```

---

## 9. 验证与压测方法

### 9.1 模拟热 Key

```bash
# 对同一题目 id 高并发
ab -n 10000 -c 200 "http://localhost:8101/api/question/get/vo?id=2"
```

预期：

- `HotKeyDetector` 标记 `question:detail:2` 为热 Key
- 日志输出 Top1 为该 key
- 开启 L1 后 Redis `GET` QPS 下降

### 9.2 Redis 侧验证

```bash
redis-cli --hotkeys
redis-cli INFO stats | grep instantaneous_ops_per_sec
```

### 9.3 面试演示脚本

1. 压测前：展示 Redis `INFO` ops 与 P99
2. 压测中：同一 `id=2` 打满
3. 展示应用日志 `HotKey Top10: question:detail:2 = 8521`
4. 开启 L1 后再压一轮，对比 Redis 命令数下降

---

## 10. 面试话术（2 分钟）

> 题目详情上了 Redis 之后，真正的问题是**访问分布不均**：模拟卷第一题、班级统一作业会让某个 `question:detail:{id}` 成为热 Key，单机 Redis 容易打满。  
> 我们没有一上来就上 Proxy，而是做了**应用层热 Key 探测**：在缓存读路径对 key 做滑动窗口计数，超过阈值标记为热 Key，并触发 **Caffeine 本地二级缓存** 和 **主动续期**，把压力从 Redis 再挡一层。  
> 运维侧用 Redis `--hotkeys` 做交叉验证。  
> 如果未来上 Cluster，可以再演进**热 Key 读扩散**（一个逻辑 key 映射多个物理副本）。  

---

## 11. 方案选型结论


| 阶段           | 探测                                 | 治理                        |
| ------------ | ---------------------------------- | ------------------------- |
| **现在（面试项目）** | `LocalHotKeyDetector` + 定时 TopN 日志 | 热 Key 走 Caffeine L1 + 互斥锁 |
| **小规模上线**    | 叠加 `redis-cli --hotkeys` 日巡检       | 管理端「热题榜」展示                |
| **多实例**      | `RedisHotKeyDetector` 分布式计数        | 同上 + 告警（钉钉/Webhook）       |
| **大规模**      | 云 Redis 热 Key 分析 / 自研 Proxy        | 读扩散、独立热 Key 节点            |


**不建议**：生产环境长期开 `MONITOR`；不建议 Micrometer 对每一个 `questionId` 打全量 tag。

---

## 12. 相关文档


| 文档                                  | 关系                    |
| ----------------------------------- | --------------------- |
| [题目详情Redis缓存方案](./题目详情Redis缓存方案.md) | 热 Key 探测的挂接点与击穿治理     |
| [数学刷题网站设计与技术亮点](./数学刷题网站设计与技术亮点.md) | 缓存三大问题、排行榜场景          |
| [系统业务梳理](./系统业务梳理.md)               | Redis 现有用途（签到 BitSet） |


---

## 13. 参考资料

- Redis 官方：`MEMORY DOCTOR`、`--hotkeys`（需 LFU 策略）
- 《Redis 设计与实现》— 单线程模型与热点问题
- 阿里技术：热点 Key 解决方案（本地缓存、读扩散、热 Key 备份）
- Caffeine 文档：W-TinyLFU 本地缓存（适合 L1）

---

## 14. 后续可落地任务（可选）

- [ ] 实现 `HotKeyDetector` + `HotKeyProperties`
- [ ] 接入 `QuestionCacheService.getFromCache`
- [ ] 增加 `HotKeyReportJob` 定时日志
- [ ] 热 Key 命中时启用 Caffeine 本地缓存
- [ ] 管理端接口 `GET /admin/hotkey/top`（仅 admin）
- [ ] 压测文档与对比数据截图（面试材料）