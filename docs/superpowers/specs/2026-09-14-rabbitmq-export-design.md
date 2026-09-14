# RabbitMQ 可靠异步导出设计

## 目标

将试卷导出从进程内 `Spring Event + @Async` 改为基于 RabbitMQ 的可恢复异步任务。提交接口仍然立即返回 `taskId`，前端现有的轮询、留档和下载接口保持不变。

这是单体应用内部的可靠任务队列，不拆分新服务。RabbitMQ 消息只携带 `taskId`，业务事实和任务状态继续以 MySQL `export_task` 为准。

## 范围

### 包含

- 持久化的主交换机、主队列、重试队列和死信队列。
- 事务提交后投递 `taskId`。
- Publisher Confirm 和 Return 日志。
- 消费者原子抢占任务，抵御重复消息和并发消费。
- 最多 3 次延迟重试，超限后标记失败并投递死信队列。
- 定时恢复长时间 `pending` 和超时 `processing` 任务。
- 数据表字段、应用配置、单元测试和项目文档的同步更新。

### 不包含

- 微服务拆分、Kafka 或分布式事务框架。
- RabbitMQ 延迟消息插件。
- 单独的 Outbox 表。
- DOCX/HTML 导出、管理后台和新的前端交互。

## 组件

### RabbitMQ 拓扑

- `math.exam.export.exchange`：主交换机。
- `math.exam.export.queue`：主队列，路由键 `export.request`。
- `math.exam.export.retry.exchange` 和 `math.exam.export.retry.queue`：固定 TTL 的延迟重试队列，到期后通过 DLX 返回主交换机。
- `math.exam.export.dead.exchange` 和 `math.exam.export.dead.queue`：保留超过重试上限的消息，便于排查。

所有交换机和队列都持久化。不依赖延迟插件，本地 RabbitMQ 即可运行。

### 生产者

`submitExport` 仍在事务内创建 `pending` 记录。事务提交后，现有领域事件监听器不再启动线程池，而是调用 `ExportMessagePublisher` 向 RabbitMQ 发送持久化消息。

如果 Broker 不可用，当次投递记录错误，任务继续保持 `pending`，由恢复任务稍后重投。这在不引入 Outbox 表的前提下关闭“数据库成功、消息未发送”的丢任务窗口。

### 消费者与导出执行器

`ExportMessageConsumer` 收到 `taskId` 后先执行条件更新：只有 `pending -> processing` 成功的消费者可以执行导出。其他重复消息直接确认，不重复生成或上传文件。

原 `executeExportAsync` 重命名为表达同步工作语义的 `executeExport`，专注于题面快照、FreeMarker 渲染、Gotenberg 转换、COS 上传和成功状态更新。重试策略由消费编排层负责，避免业务执行器吞掉异常。

## 状态和数据

`export_task` 新增：

- `retryCount`：已失败的执行次数，默认 0。

重试期间的最近错误继续写入现有 `errorMsg`，不增加语义重复的字段。

增加 `(status, updateTime)` 索引，支持恢复任务扫描。

状态流转：

```text
pending -> processing -> success
                     -> pending  (可重试失败)
                     -> failed   (超过上限)
processing(超时) -> pending
```

更新必须带期望状态条件，不使用无条件 `updateById` 实现抢占或重试状态切换。

## 失败与确认

- 业务导出成功后确认主队列消息。
- 可重试失败：先把数据库状态恢复为 `pending` 并增加 `retryCount`，再投递到重试队列，成功后确认原消息。若重试投递失败，仍确认原消息，留给 `pending` 恢复扫描补发，避免立即红递归风暴。
- 超过上限：更新为 `failed`，再投递死信队列供诊断。
- 应用在执行中崩溃：消息可能红递，但任务仍是 `processing`；消费者不并发重做，由超时恢复任务重置后重投。

## 恢复任务

定时任务按有界批次处理：

1. 将超过配置时间的 `processing` 条件更新回 `pending`。
2. 查询长时间 `pending` 的任务并重新投递。
3. 每次最多处理配置的数量，避免定时任务放大故障。

恢复时允许重复投递，正确性由消费者的状态抢占保证。

## 配置

RabbitMQ 连接使用 Spring Boot 标准 `spring.rabbitmq.*` 配置。业务参数使用 `export.mq.*`：

- 重试延迟；
- 最大重试次数；
- `processing` 超时时间；
- 恢复扫描周期与批次大小；
- 消费者并发数和 prefetch。

配置提供适合本地开发的默认值，密码支持环境变量覆盖。

## 测试

- 提交新任务后只发送 `taskId`。
- 命中已有 `pending` 幂等任务时可重投。
- 两条相同消息只有一条能把状态改为 `processing`。
- 导出成功更新快照、文件信息和 `success`。
- 可重试异常增加次数、恢复 `pending` 并发送到重试队列。
- 第 3 次失败后标记 `failed` 并发送死信。
- 重复消息、不存在任务和终态任务均不执行导出。
- 恢复任务能处理过期 `processing` 和滞留 `pending`。
- RabbitMQ 不可用时，已落库任务保持可恢复状态。

测试以 Mockito 单元测试覆盖编排和状态机，不要求外部 MySQL、COS 或 Gotenberg。项目已有的 `contextLoads` 仍需要独立的 JWT 测试密钥问题，与 MQ 测试分开处理。

## 文档和运行

- README 增加 RabbitMQ 依赖和启动说明。
- 导出流程文档从线程池更新为 MQ 、重试、死信和恢复链路。
- 提供只启动 RabbitMQ 的 Docker Compose，不将应用拆成容器化微服务。
