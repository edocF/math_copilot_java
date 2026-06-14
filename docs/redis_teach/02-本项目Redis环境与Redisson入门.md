# 02 - 本项目 Redis 环境与 Redisson 入门

---

## 1. 本地安装 Redis（Windows）

### 方式 A：Docker（推荐）

```bash
docker run -d --name redis -p 6379:6379 redis:7
```

### 方式 B：WSL / Memurai / 安装包

确保本机 `6379` 端口可访问即可。

### 验证

```bash
redis-cli ping
# 应返回 PONG
```

---

## 2. 项目中的 Redis 配置

文件：`src/main/resources/application.yml`

```yaml
spring:
  redis:
    database: 0
    host: localhost
    port: 6379
    timeout: 5000
```

Redisson 配置类：`config/RedissonConfig.java`

```java
@Configuration
@ConfigurationProperties(prefix = "spring.redis")
public class RedissonConfig {
    private String host;
    private Integer port;
    private String password;
    private Integer database;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setPassword(password)
                .setDatabase(database);
        return Redisson.create(config);
    }
}
```

**你要做的**：启动 Redis → 启动 Spring Boot → 签到接口能正常用，说明 Redisson 连通。

---

## 3. 用 redis-cli 操作 String（最基础）

题目缓存本质是 **String 类型**的 key-value。

```bash
redis-cli

# 写入
SET question:detail:2 "{\"id\":2,\"content\":\"题干...\"}"

# 读取
GET question:detail:2

# 设置 60 秒后过期
SETEX question:detail:2 60 "{\"id\":2}"

# 查看剩余秒数
TTL question:detail:2

# 删除
DEL question:detail:2
```

Java 里不会手写 `SET` 命令，而是用 `RBucket`，底层就是 String。

---

## 4. 项目已有示例：签到 BitSet（导读）

文件：`UserServiceImpl.java`

```java
String key = RedisConstant.getUserSingInRedisKey(year, id);
// key 形如：user:signins:2026:1

RBitSet bitSet = redissonClient.getBitSet(key);
bitSet.set(dayOfYear, true);  // 第 n 天签到
bitSet.get(dayOfYear);        // 查询是否签到
```

`RedisConstant.java`：

```java
String USER_SING_IN_REDIS_KEY_PREFIX = "user:signins";

static String getUserSingInRedisKey(int year, long userId) {
    return String.format("%s:%s:%s", USER_SING_IN_REDIS_KEY_PREFIX, year, userId);
}
```

**和题目缓存的共同点**：

- 都用 `RedissonClient` 注入  
- 都有**统一的 key 生成方法**（不要散落在代码里拼字符串）  
- key 带业务前缀，避免冲突  

**不同点**：

| 签到 | 题目详情 |
|------|----------|
| `RBitSet` | `RBucket<String>` |
| 按位存 0/1 | 存整段 JSON |
| 一年一个 key | 一题一个 key |

---

## 5. Redisson RBucket 入门

`RBucket` = 操作单个 Redis String 的 Java 对象。

```java
@RequiredArgsConstructor
@Service
public class DemoCacheService {
    private final RedissonClient redissonClient;

    public void demo() {
        String key = "question:detail:2";
        RBucket<String> bucket = redissonClient.getBucket(key);

        // 写入，30 分钟过期
        bucket.set("{\"id\":2}", 30, TimeUnit.MINUTES);

        // 读取
        String json = bucket.get();

        // 删除
        bucket.delete();

        // 是否存在
        boolean exists = bucket.isExists();
    }
}
```

题目缓存全程围绕 `get` / `set` / `delete` 三个动作展开。

---

## 6. 在 Spring 里注入 RedissonClient

任何 `@Service` 中：

```java
@RequiredArgsConstructor
public class QuestionCacheServiceImpl implements QuestionCacheService {
    private final RedissonClient redissonClient;
    // ...
}
```

与注入 `QuestionMapper` 一样，由 Spring 容器管理，**不要**自己 `Redisson.create()`。

---

## 7. 常见连接问题排错

| 现象 | 可能原因 | 处理 |
|------|----------|------|
| `Unable to connect to Redis` | Redis 没启动 | `docker start redis` 或启动服务 |
| 连接超时 | host/port 错 | 检查 `application.yml` |
| 密码错误 | 配置了 password | 本地可留空 |
| 项目能启动但缓存不生效 | 未调用缓存代码 | 看第 ⑥ 课实现是否接入 |

开发时若不想依赖 Redis，后续会加开关 `cache.question.enabled=false` 降级查 DB。

---

## 8. Redis 数据结构速览（扩展）

题目缓存只用 **String**。了解其他的有助于面试和签到代码：

| 类型 | 典型用途 | 本项目 |
|------|----------|--------|
| String | 缓存 JSON、计数 | **题目详情** |
| Hash | 对象多个字段 | 可选存题目字段 |
| List | 队列、时间线 | 未用 |
| Set | 去重集合 | 未用 |
| ZSet | 排行榜 | 可做错题热度榜 |
| BitMap | 签到、布隆 | **签到** |

---

## 9. 动手练习（5 分钟）

1. 启动 Redis，确认 `PING`  
2. 启动 `interview_copilot`，调用签到或任意需 Redis 的接口  
3. 在 `redis-cli` 执行 `KEYS user:signins:*` 看是否有 key（生产禁用 KEYS，本地学习可用）  
4. 用 `SET` / `GET` 手动模拟 `question:detail:2`

---

## 10. 自测

1. `RedissonClient` 在项目里哪里创建的？  
2. `RBucket` 和 `RBitSet` 分别适合什么数据？  
3. 题目缓存的 key 建议命名成什么格式？

下一章：**[03-缓存设计基础 Key-TTL与序列化](./03-缓存设计基础-Key-TTL与序列化.md)**
