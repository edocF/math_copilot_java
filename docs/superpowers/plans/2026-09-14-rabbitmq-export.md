# RabbitMQ Reliable Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace in-process exam export execution with a durable RabbitMQ workflow that is idempotent, retryable, dead-lettered, and recoverable without splitting the monolith.

**Architecture:** `export_task` remains the source of truth. A transaction-after-commit listener publishes only `taskId`; a RabbitMQ consumer atomically claims `pending` tasks, runs the existing export pipeline, and records success or retry state. Fixed-TTL retry and dead-letter queues plus a bounded recovery job cover transient failures, duplicate delivery, publisher failure, and worker crashes.

**Tech Stack:** Java 8, Spring Boot 2.7.2, Spring AMQP, RabbitMQ, MyBatis-Plus, MySQL, JUnit 5, Mockito, Docker Compose

**Spec:** `docs/superpowers/specs/2026-09-14-rabbitmq-export-design.md`

## Global Constraints

- Keep one Spring Boot monolith; do not introduce a new deployable service.
- RabbitMQ messages contain only `taskId`; MySQL remains the business source of truth.
- Use durable direct exchanges and durable queues; do not require the delayed-message plugin.
- Retry at most 3 failed executions through a fixed-TTL retry queue.
- Do not add Kafka, an Outbox table, or a distributed transaction framework.
- Preserve all existing export HTTP request and response contracts.
- Do not mix the user's existing Elasticsearch, configuration, or README edits into feature commits unless this plan names the file.

---

## File Map

**Create**

- `src/main/java/com/fu/math_copilot/config/ExportMqProperties.java` — typed retry, recovery, and consumer settings.
- `src/main/java/com/fu/math_copilot/config/ExportRabbitConfig.java` — durable exchanges, queues, bindings, and message converter.
- `src/main/java/com/fu/math_copilot/mq/ExportTaskMessage.java` — serialized `taskId` envelope.
- `src/main/java/com/fu/math_copilot/mq/ExportMessagePublisher.java` — main, retry, and dead-letter publishing.
- `src/main/java/com/fu/math_copilot/mq/ExportMessageConsumer.java` — claim, execute, retry, dead-letter, and acknowledge orchestration.
- `src/main/java/com/fu/math_copilot/service/ExportTaskStateManager.java` — database-backed export state transitions.
- `src/main/java/com/fu/math_copilot/job/cycle/ExportTaskRecoveryJob.java` — bounded recovery of stale tasks.
- `src/main/java/com/fu/math_copilot/sql/upgrade_export_task_mq.sql` — one-time migration for an existing database.
- `src/test/java/com/fu/math_copilot/config/ExportRabbitConfigTest.java`
- `src/test/java/com/fu/math_copilot/service/ExportTaskStateManagerTest.java`
- `src/test/java/com/fu/math_copilot/mq/ExportMessagePublisherTest.java`
- `src/test/java/com/fu/math_copilot/mq/ExportMessageConsumerTest.java`
- `src/test/java/com/fu/math_copilot/job/cycle/ExportTaskRecoveryJobTest.java`
- `docker-compose.yml` — local RabbitMQ only.

**Modify**

- `pom.xml` — add Spring AMQP.
- `src/main/resources/application-test.yml` — local RabbitMQ and export MQ defaults.
- `src/main/resources/application-prod.yml` — environment-overridable RabbitMQ and export MQ settings.
- `src/main/java/com/fu/math_copilot/model/entity/ExportTask.java` — add `retryCount`.
- `src/main/java/com/fu/math_copilot/mapper/ExportTaskMapper.java` — conditional state-transition and recovery queries.
- `src/main/java/com/fu/math_copilot/sql/create_table.sql` — add retry column and recovery index.
- `src/main/java/com/fu/math_copilot/service/ExportTaskService.java` — rename the execution contract to `executeExport`.
- `src/main/java/com/fu/math_copilot/service/impl/ExportTaskServiceImpl.java` — publish after commit as before, but make export execution synchronous and exception-transparent.
- `src/main/java/com/fu/math_copilot/listener/ExportTaskEventListener.java` — replace `@Async` execution with RabbitMQ publishing.
- `src/main/java/com/fu/math_copilot/config/AsyncConfig.java` — remove only the export executor; keep the question-bank executor.
- `src/main/java/com/fu/math_copilot/controller/ExamPaperController.java` — update stale JavaDoc only.
- `README.md`, `docs/03-核心业务流程.md`, `docs/04-核心技术实现.md`, `docs/09-导出任务面试故事.md` — document the implemented MQ flow.

---

### Task 1: RabbitMQ Dependency, Properties, and Durable Topology

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/com/fu/math_copilot/config/ExportMqProperties.java`
- Create: `src/main/java/com/fu/math_copilot/config/ExportRabbitConfig.java`
- Create: `src/main/java/com/fu/math_copilot/mq/ExportTaskMessage.java`
- Test: `src/test/java/com/fu/math_copilot/config/ExportRabbitConfigTest.java`

**Interfaces:**
- Produces: `ExportMqProperties` getters for `retryDelayMillis`, `maxRetries`, `processingTimeoutMillis`, `pendingRepublishMillis`, `recoveryFixedDelayMillis`, and `recoveryBatchSize`.
- Produces: `ExportRabbitConfig.MAIN_EXCHANGE`, `MAIN_QUEUE`, `MAIN_ROUTING_KEY`, `RETRY_EXCHANGE`, `RETRY_QUEUE`, `RETRY_ROUTING_KEY`, `DEAD_EXCHANGE`, `DEAD_QUEUE`, and `DEAD_ROUTING_KEY`.
- Produces: `ExportTaskMessage(Long taskId)` with JavaBean serialization support.

- [ ] **Step 1: Add a failing topology test**

```java
class ExportRabbitConfigTest {
    private final ExportMqProperties properties = new ExportMqProperties();
    private final ExportRabbitConfig config = new ExportRabbitConfig(properties);

    @Test
    void retryQueueReturnsExpiredMessagesToMainRoute() {
        properties.setRetryDelayMillis(30_000L);
        Queue queue = config.exportRetryQueue();
        assertEquals(30_000L, queue.getArguments().get("x-message-ttl"));
        assertEquals(ExportRabbitConfig.MAIN_EXCHANGE,
                queue.getArguments().get("x-dead-letter-exchange"));
        assertEquals(ExportRabbitConfig.MAIN_ROUTING_KEY,
                queue.getArguments().get("x-dead-letter-routing-key"));
        assertTrue(queue.isDurable());
    }
}
```

- [ ] **Step 2: Run the test and verify the missing classes fail compilation**

Run: `mvn -Dtest=ExportRabbitConfigTest test`

Expected: FAIL because `ExportMqProperties` and `ExportRabbitConfig` do not exist.

- [ ] **Step 3: Add Spring AMQP and the message envelope**

Add to `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

Create a Java 8-compatible message type:

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExportTaskMessage implements Serializable {
    private Long taskId;
    private static final long serialVersionUID = 1L;
}
```

- [ ] **Step 4: Implement typed properties and topology**

Use `@ConfigurationProperties(prefix = "export.mq")` with these defaults:

```java
private long retryDelayMillis = 30_000L;
private int maxRetries = 3;
private long processingTimeoutMillis = 10 * 60_000L;
private long pendingRepublishMillis = 60_000L;
private long recoveryFixedDelayMillis = 60_000L;
private int recoveryBatchSize = 100;
```

Declare durable `DirectExchange` beans and queues. Build the retry queue exactly as follows:

```java
return QueueBuilder.durable(RETRY_QUEUE)
        .ttl(properties.getRetryDelayMillis())
        .deadLetterExchange(MAIN_EXCHANGE)
        .deadLetterRoutingKey(MAIN_ROUTING_KEY)
        .build();
```

Bind main, retry, and dead queues to their corresponding exchanges and provide a `Jackson2JsonMessageConverter` bean.

- [ ] **Step 5: Run the focused test**

Run: `mvn -Dtest=ExportRabbitConfigTest test`

Expected: PASS with 1 test and 0 failures.

- [ ] **Step 6: Commit the topology**

```bash
git add pom.xml src/main/java/com/fu/math_copilot/config/ExportMqProperties.java src/main/java/com/fu/math_copilot/config/ExportRabbitConfig.java src/main/java/com/fu/math_copilot/mq/ExportTaskMessage.java src/test/java/com/fu/math_copilot/config/ExportRabbitConfigTest.java
git commit -m "feat: configure RabbitMQ export topology"
```

---

### Task 2: Atomic Database State Transitions

**Files:**
- Modify: `src/main/java/com/fu/math_copilot/model/entity/ExportTask.java`
- Modify: `src/main/java/com/fu/math_copilot/mapper/ExportTaskMapper.java`
- Modify: `src/main/java/com/fu/math_copilot/sql/create_table.sql`
- Create: `src/main/java/com/fu/math_copilot/sql/upgrade_export_task_mq.sql`
- Create: `src/main/java/com/fu/math_copilot/service/ExportTaskStateManager.java`
- Test: `src/test/java/com/fu/math_copilot/service/ExportTaskStateManagerTest.java`

**Interfaces:**
- Consumes: `ExportMqProperties#getMaxRetries()` and `#getRecoveryBatchSize()`.
- Produces: `boolean tryMarkProcessing(Long taskId)`.
- Produces: `FailureDisposition recordFailure(Long taskId, String errorMessage)`, where the enum values are `RETRY` and `DEAD`.
- Produces: `List<Long> recoverTimedOutProcessing(Date cutoff, int limit)` and `List<Long> findStalePending(Date cutoff, int limit)`.
- Produces: `void touchPendingDispatch(Long taskId)`.

- [ ] **Step 1: Write failing state-manager tests**

Cover claim success, duplicate claim, retry before the limit, and terminal failure:

```java
@Test
void duplicateMessageCannotClaimProcessingTask() {
    when(mapper.claimPending(42L)).thenReturn(0);
    assertFalse(manager.tryMarkProcessing(42L));
    verify(mapper).claimPending(42L);
}

@Test
void thirdFailureBecomesTerminal() {
    ExportTask task = new ExportTask();
    task.setId(42L);
    task.setStatus("processing");
    task.setRetryCount(2);
    when(mapper.selectById(42L)).thenReturn(task);
    when(mapper.markFailedFromProcessing(eq(42L), anyString())).thenReturn(1);
    assertEquals(FailureDisposition.DEAD,
            manager.recordFailure(42L, "gotenberg unavailable"));
}
```

- [ ] **Step 2: Run the state-manager test and verify failure**

Run: `mvn -Dtest=ExportTaskStateManagerTest test`

Expected: FAIL because the state manager and mapper methods do not exist.

- [ ] **Step 3: Add the retry column and recovery index**

Add `private Integer retryCount;` to `ExportTask`. Change `create_table.sql` to include:

```sql
retryCount int default 0 not null comment '已失败的执行次数',
index idx_status_updateTime (status, updateTime)
```

Create `upgrade_export_task_mq.sql` for an existing database. It is explicitly a run-once migration:

```sql
ALTER TABLE export_task
    ADD COLUMN retryCount int DEFAULT 0 NOT NULL COMMENT '已失败的执行次数' AFTER idempotentKey,
    ADD INDEX idx_status_updateTime (status, updateTime);
```

- [ ] **Step 4: Add exact conditional mapper operations**

Use MyBatis annotations with these signatures:

```java
int claimPending(@Param("taskId") Long taskId);
int rescheduleFromProcessing(@Param("taskId") Long taskId,
                             @Param("errorMsg") String errorMsg);
int markFailedFromProcessing(@Param("taskId") Long taskId,
                             @Param("errorMsg") String errorMsg);
List<Long> findIdsByStatusBefore(@Param("status") String status,
                                 @Param("before") Date before,
                                 @Param("limit") int limit);
int resetTimedOutProcessing(@Param("taskId") Long taskId,
                            @Param("before") Date before);
int touchPendingDispatch(@Param("taskId") Long taskId);
```

`claimPending` must execute `UPDATE export_task SET status='processing', progress=10, updateTime=NOW() WHERE id=#{taskId} AND status='pending'`. Retry and terminal updates must require `status='processing'` and increment `retryCount` atomically.

- [ ] **Step 5: Implement `ExportTaskStateManager`**

Clamp errors with `StrUtil.sub(errorMessage, 0, 500)`. For a claimed task, calculate `nextRetryCount = defaultIfNull(task.getRetryCount(), 0) + 1`; use `RETRY` when `nextRetryCount < properties.getMaxRetries()`, otherwise use `DEAD`. Throw `BusinessException(OPERATION_ERROR)` if the expected conditional update affects zero rows, because that signals a state race.

Recovery must query at most `Math.min(Math.max(limit, 1), properties.getRecoveryBatchSize())` IDs and conditionally reset each timed-out processing task before returning only IDs that were actually reset.

- [ ] **Step 6: Run focused state tests**

Run: `mvn -Dtest=ExportTaskStateManagerTest test`

Expected: PASS for claim, retry, terminal failure, bounded query, and timeout reset tests.

- [ ] **Step 7: Commit state transitions**

```bash
git add src/main/java/com/fu/math_copilot/model/entity/ExportTask.java src/main/java/com/fu/math_copilot/mapper/ExportTaskMapper.java src/main/java/com/fu/math_copilot/sql/create_table.sql src/main/java/com/fu/math_copilot/sql/upgrade_export_task_mq.sql src/main/java/com/fu/math_copilot/service/ExportTaskStateManager.java src/test/java/com/fu/math_copilot/service/ExportTaskStateManagerTest.java
git commit -m "feat: add atomic export task state transitions"
```

---

### Task 3: Exception-Transparent Export Execution

**Files:**
- Modify: `src/main/java/com/fu/math_copilot/service/ExportTaskService.java`
- Modify: `src/main/java/com/fu/math_copilot/service/impl/ExportTaskServiceImpl.java`
- Modify: `src/main/java/com/fu/math_copilot/controller/ExamPaperController.java`
- Test: `src/test/java/com/fu/math_copilot/service/ExportTaskExecutionTest.java`

**Interfaces:**
- Produces: `void executeExport(Long taskId)`; the caller must have already claimed the task.
- Removes: `void executeExportAsync(Long taskId)`.
- Keeps: existing `submitExport`, status, snapshot, paging, progress, success, and access-control contracts.

- [ ] **Step 1: Write a failing execution failure test**

Build `ExportTaskServiceImpl` with mocked `ExamPaperService`, `CosManager`, `ExamPaperExportHelper`, and `GotenbergClient`. Configure `GotenbergClient#convertHtmlToPdf` to throw and assert:

```java
assertThrows(BusinessException.class, () -> service.executeExport(42L));
verify(mapper, never()).updateById(argThat(task -> "failed".equals(task.getStatus())));
```

This proves the execution layer no longer swallows failures or makes retry decisions.

- [ ] **Step 2: Run the execution test and verify failure**

Run: `mvn -Dtest=ExportTaskExecutionTest test`

Expected: FAIL because only `executeExportAsync` exists and catches the exception.

- [ ] **Step 3: Refactor the execution method**

Rename the interface and implementation method to `executeExport`. Remove the initial `pending` check and `markProcessing` call because the consumer owns the claim. Keep temporary-file cleanup in `finally`, but remove the catch block that calls `markFailed`; let the original exception propagate to the consumer.

Update `ExamPaperController` JavaDoc to reference `ExportTaskService#executeExport`.

- [ ] **Step 4: Run the focused execution test**

Run: `mvn -Dtest=ExportTaskExecutionTest test`

Expected: PASS; conversion failures escape and no failed state is written by the executor.

- [ ] **Step 5: Commit the executor refactor**

```bash
git add src/main/java/com/fu/math_copilot/service/ExportTaskService.java src/main/java/com/fu/math_copilot/service/impl/ExportTaskServiceImpl.java src/main/java/com/fu/math_copilot/controller/ExamPaperController.java src/test/java/com/fu/math_copilot/service/ExportTaskExecutionTest.java
git commit -m "refactor: separate export execution from retry policy"
```

---

### Task 4: Transaction-After-Commit RabbitMQ Publisher

**Files:**
- Create: `src/main/java/com/fu/math_copilot/mq/ExportMessagePublisher.java`
- Modify: `src/main/java/com/fu/math_copilot/listener/ExportTaskEventListener.java`
- Modify: `src/main/java/com/fu/math_copilot/config/AsyncConfig.java`
- Test: `src/test/java/com/fu/math_copilot/mq/ExportMessagePublisherTest.java`
- Test: `src/test/java/com/fu/math_copilot/listener/ExportTaskEventListenerTest.java`

**Interfaces:**
- Consumes: `ExportRabbitConfig` exchange and routing constants.
- Produces: `void sendMain(Long taskId)`, `void sendRetry(Long taskId)`, and `void sendDead(Long taskId)`.
- Keeps: `ExportTaskSubmittedEvent` as the transaction-bound domain event.

- [ ] **Step 1: Write failing publisher and listener tests**

Publisher test:

```java
publisher.sendMain(42L);
verify(rabbitTemplate).convertAndSend(
        eq(ExportRabbitConfig.MAIN_EXCHANGE),
        eq(ExportRabbitConfig.MAIN_ROUTING_KEY),
        eq(new ExportTaskMessage(42L)),
        any(MessagePostProcessor.class),
        any(CorrelationData.class));
```

Listener test:

```java
listener.onExportTaskSubmitted(new ExportTaskSubmittedEvent(this, 42L));
verify(publisher).sendMain(42L);
verifyNoInteractions(exportTaskService);
```

- [ ] **Step 2: Run tests and verify they fail**

Run: `mvn -Dtest=ExportMessagePublisherTest,ExportTaskEventListenerTest test`

Expected: FAIL because publisher methods do not exist and the listener invokes the executor.

- [ ] **Step 3: Implement persistent publishing and callbacks**

For every send, use a `MessagePostProcessor` to set `MessageDeliveryMode.PERSISTENT`, set correlation ID to `taskId.toString()`, and pass `new CorrelationData(taskId.toString())`. Configure `rabbitTemplate.setMandatory(true)`, a confirm callback that logs negative acknowledgements, and a returns callback that logs unroutable messages without logging message bodies.

- [ ] **Step 4: Replace event execution with publishing**

Keep `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` and remove `@Async`. The listener body becomes:

```java
public void onExportTaskSubmitted(ExportTaskSubmittedEvent event) {
    exportMessagePublisher.sendMain(event.getTaskId());
}
```

Remove `EXPORT_EXECUTOR` and `exportExecutor()` from `AsyncConfig`; retain `@EnableAsync` and the question-bank executor because another feature uses it.

- [ ] **Step 5: Run focused publisher/listener tests**

Run: `mvn -Dtest=ExportMessagePublisherTest,ExportTaskEventListenerTest test`

Expected: PASS; the transaction listener publishes and never executes export work.

- [ ] **Step 6: Commit publishing**

```bash
git add src/main/java/com/fu/math_copilot/mq/ExportMessagePublisher.java src/main/java/com/fu/math_copilot/listener/ExportTaskEventListener.java src/main/java/com/fu/math_copilot/config/AsyncConfig.java src/test/java/com/fu/math_copilot/mq/ExportMessagePublisherTest.java src/test/java/com/fu/math_copilot/listener/ExportTaskEventListenerTest.java
git commit -m "feat: publish export tasks after transaction commit"
```

---

### Task 5: Idempotent Consumer, Retry, and Dead Letter

**Files:**
- Create: `src/main/java/com/fu/math_copilot/mq/ExportMessageConsumer.java`
- Test: `src/test/java/com/fu/math_copilot/mq/ExportMessageConsumerTest.java`

**Interfaces:**
- Consumes: `ExportTaskStateManager#tryMarkProcessing`, `#recordFailure`.
- Consumes: `ExportTaskService#executeExport`.
- Consumes: `ExportMessagePublisher#sendRetry` and `#sendDead`.
- Produces: `void consume(ExportTaskMessage payload, Message message, Channel channel)` bound to `ExportRabbitConfig.MAIN_QUEUE` with manual acknowledgement.

- [ ] **Step 1: Write failing consumer tests**

Cover these exact scenarios:

```java
@Test
void duplicateDeliveryIsAcknowledgedWithoutExecution() throws Exception {
    when(stateManager.tryMarkProcessing(42L)).thenReturn(false);
    consumer.consume(payload(42L), messageWithTag(7L), channel);
    verify(exportTaskService, never()).executeExport(anyLong());
    verify(channel).basicAck(7L, false);
}

@Test
void transientFailureSchedulesRetryThenAcknowledgesOriginal() throws Exception {
    when(stateManager.tryMarkProcessing(42L)).thenReturn(true);
    doThrow(new RuntimeException("temporary")).when(exportTaskService).executeExport(42L);
    when(stateManager.recordFailure(eq(42L), anyString())).thenReturn(FailureDisposition.RETRY);
    consumer.consume(payload(42L), messageWithTag(8L), channel);
    verify(publisher).sendRetry(42L);
    verify(channel).basicAck(8L, false);
}
```

Also test success, terminal failure to dead exchange, invalid/null task ID, and retry-publish failure leaving the database decision intact while acknowledging the original delivery.

- [ ] **Step 2: Run the consumer test and verify failure**

Run: `mvn -Dtest=ExportMessageConsumerTest test`

Expected: FAIL because `ExportMessageConsumer` does not exist.

- [ ] **Step 3: Implement manual-ack orchestration**

Annotate with:

```java
@RabbitListener(queues = ExportRabbitConfig.MAIN_QUEUE, ackMode = "MANUAL")
```

Reject invalid payloads by logging and acknowledging. If claim returns false, acknowledge as a harmless duplicate. On success, acknowledge after `executeExport`. On exception, call `recordFailure`; publish to retry or dead exchange according to the returned disposition; catch publisher failures so the pending database state can be recovered; finally acknowledge the original message. Never use `basicNack(..., true)`, which would create an immediate redelivery loop.

- [ ] **Step 4: Run focused consumer tests**

Run: `mvn -Dtest=ExportMessageConsumerTest test`

Expected: PASS for success, duplicate, retry, terminal, invalid payload, and publisher-failure cases.

- [ ] **Step 5: Commit the consumer**

```bash
git add src/main/java/com/fu/math_copilot/mq/ExportMessageConsumer.java src/test/java/com/fu/math_copilot/mq/ExportMessageConsumerTest.java
git commit -m "feat: consume export tasks with retry and dead letter"
```

---

### Task 6: Bounded Recovery Job

**Files:**
- Create: `src/main/java/com/fu/math_copilot/job/cycle/ExportTaskRecoveryJob.java`
- Test: `src/test/java/com/fu/math_copilot/job/cycle/ExportTaskRecoveryJobTest.java`

**Interfaces:**
- Consumes: timeout and batch values from `ExportMqProperties`.
- Consumes: `ExportTaskStateManager#recoverTimedOutProcessing`, `#findStalePending`, and `#touchPendingDispatch`.
- Consumes: `ExportMessagePublisher#sendMain`.
- Produces: `void recover()` scheduled with `${export.mq.recovery-fixed-delay-millis:60000}`.

- [ ] **Step 1: Write failing recovery tests**

```java
@Test
void resetsTimedOutTasksAndRepublishesStalePendingTasksOnce() {
    when(stateManager.recoverTimedOutProcessing(any(Date.class), eq(100)))
            .thenReturn(Arrays.asList(1L, 2L));
    when(stateManager.findStalePending(any(Date.class), eq(98)))
            .thenReturn(Arrays.asList(3L));
    job.recover();
    verify(publisher).sendMain(1L);
    verify(publisher).sendMain(2L);
    verify(publisher).sendMain(3L);
    verify(stateManager).touchPendingDispatch(1L);
    verify(stateManager).touchPendingDispatch(2L);
    verify(stateManager).touchPendingDispatch(3L);
}
```

Also assert that one publish failure does not stop later tasks and that the combined work never exceeds the configured batch size.

- [ ] **Step 2: Run the recovery test and verify failure**

Run: `mvn -Dtest=ExportTaskRecoveryJobTest test`

Expected: FAIL because the recovery job does not exist.

- [ ] **Step 3: Implement the recovery job**

Calculate cutoffs with `new Date(System.currentTimeMillis() - timeout)`. Recover timed-out processing tasks first, then use the remaining batch capacity for stale pending tasks. For each ID, call `sendMain`; only after a successful send call `touchPendingDispatch`. Catch and log per-task publishing errors so the next scheduled run can retry them.

- [ ] **Step 4: Run recovery and full feature tests**

Run:

```text
mvn -Dtest=ExportRabbitConfigTest,ExportTaskStateManagerTest,ExportTaskExecutionTest,ExportMessagePublisherTest,ExportTaskEventListenerTest,ExportMessageConsumerTest,ExportTaskRecoveryJobTest test
```

Expected: PASS with 0 failures and 0 errors.

- [ ] **Step 5: Commit recovery**

```bash
git add src/main/java/com/fu/math_copilot/job/cycle/ExportTaskRecoveryJob.java src/test/java/com/fu/math_copilot/job/cycle/ExportTaskRecoveryJobTest.java
git commit -m "feat: recover stalled export tasks"
```

---

### Task 7: Runtime Configuration, Local Broker, Documentation, and Verification

**Files:**
- Modify: `src/main/resources/application-test.yml`
- Modify: `src/main/resources/application-prod.yml`
- Create: `docker-compose.yml`
- Modify: `README.md`
- Modify: `docs/03-核心业务流程.md`
- Modify: `docs/04-核心技术实现.md`
- Modify: `docs/09-导出任务面试故事.md`

**Interfaces:**
- Configures: `spring.rabbitmq.*` and `export.mq.*` without changing HTTP APIs.
- Provides: local RabbitMQ ports `5672` and management UI `15672`.

- [ ] **Step 1: Add environment-overridable runtime settings**

Use these production defaults:

```yaml
spring:
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USERNAME:guest}
    password: ${RABBITMQ_PASSWORD:guest}
    publisher-confirm-type: correlated
    publisher-returns: true
    listener:
      simple:
        acknowledge-mode: manual
        prefetch: 1
        concurrency: 2
        max-concurrency: 4

export:
  mq:
    retry-delay-millis: 30000
    max-retries: 3
    processing-timeout-millis: 600000
    pending-republish-millis: 60000
    recovery-fixed-delay-millis: 60000
    recovery-batch-size: 100
```

Use the same business defaults in `application-test.yml`; RabbitMQ connection values remain local.

- [ ] **Step 2: Add a local RabbitMQ Compose file**

Create `docker-compose.yml` with one `rabbitmq:3.13-management` service, a named data volume, ports `5672:5672` and `15672:15672`, and a health check using `rabbitmq-diagnostics -q ping`. Do not containerize the application or other dependencies in this task.

- [ ] **Step 3: Rewrite export documentation to match code**

Update README prerequisites and startup steps. Replace all claims that export execution uses `@Async` or an export thread pool with the exact flow:

```text
transaction commit -> persistent RabbitMQ message -> atomic claim
-> render/convert/upload -> success
-> fixed-delay retry (up to 3 failures) -> dead letter
```

Document duplicate delivery, publisher failure recovery, worker crash recovery, and the reason the message contains only `taskId`. Keep the interview story factual and do not claim measured throughput or reliability percentages.

- [ ] **Step 4: Verify references and compile**

Run:

```text
rg -n "executeExportAsync|EXPORT_EXECUTOR|@Async\(AsyncConfig.EXPORT_EXECUTOR\)|Spring Event \+ \u7ebf\u7a0b\u6c60" src README.md docs
mvn -DskipTests package
```

Expected: `rg` finds no stale export-executor references; Maven exits 0.

- [ ] **Step 5: Run all focused MQ tests**

Run:

```text
mvn -Dtest=ExportRabbitConfigTest,ExportTaskStateManagerTest,ExportTaskExecutionTest,ExportMessagePublisherTest,ExportTaskEventListenerTest,ExportMessageConsumerTest,ExportTaskRecoveryJobTest test
```

Expected: 0 failures and 0 errors. Do not report the pre-existing `InterviewCopilotApplicationTests.contextLoads` as passing until its separate JWT test-key dependency is fixed.

- [ ] **Step 6: Inspect the final diff for scope**

Run:

```text
git status --short
git diff --stat
git diff --check
```

Expected: no whitespace errors; only plan-listed files plus pre-existing user changes appear. Distinguish pre-existing user changes from MQ work in the handoff.

- [ ] **Step 7: Commit files that do not overlap pre-existing work**

```bash
git add docker-compose.yml src/main/resources/application-test.yml
git commit -m "chore: add local RabbitMQ runtime"
```

Leave `README.md`, `application-prod.yml`, and the currently untracked interview documents unstaged because they already contain user work outside this feature. Report those MQ documentation/configuration edits separately in the handoff rather than folding unrelated content into a feature commit.

---

## Completion Evidence

Before claiming completion, capture all of the following:

1. Focused MQ test command output with zero failures and zero errors.
2. `mvn -DskipTests package` exit code 0.
3. `rg` output proving the removed export executor and `executeExportAsync` references are gone.
4. `git diff --check` exit code 0.
5. A final status that explicitly notes the unrelated pre-existing JWT `contextLoads` failure and all pre-existing user modifications.
