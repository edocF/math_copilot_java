# 领域事件（Domain Event）实践

> 本目录是 **方案 C：`@TransactionalEventListener` + 领域事件** 的落地手册，面向 `interview_copilot`（Spring Boot 2.7.2 / Java 8）。  
> 背景与方案对比见 [事务提交后触发异步任务最佳实践](../事务提交后触发异步任务最佳实践.md) §3.2。

---

## 文档清单

| 文档 | 内容 |
|------|------|
| [01-导出任务领域事件实战](./01-导出任务领域事件实战.md) | **主文档**：从 `afterCommit` 手写回调，重构为事件驱动，含完整代码与验证步骤 |

---

## 一句话目标

> `submitExport` 只负责「写 `export_task` + 发布 `ExportTaskSubmittedEvent`」；  
> 事务 commit 后由 `ExportTaskEventListener` 异步调用 `executeExportAsync`。

## 改造前后对比

```
改造前（方案 B）                         改造后（方案 C）
─────────────────                       ─────────────────
ExportTaskServiceImpl                   ExportTaskServiceImpl
  save(task)                              save(task)
  registerSynchronization(afterCommit)    publishEvent(ExportTaskSubmittedEvent)
    runAsync(executeExportAsync)        ExportTaskEventListener
                                          @TransactionalEventListener(AFTER_COMMIT)
                                          @Async → executeExportAsync
```

## 建议阅读顺序

1. 先读 [01-导出任务领域事件实战](./01-导出任务领域事件实战.md) §1～§3（理解动机与包结构）
2. 按 §4 逐步新建/修改文件（建议开 IDE 对照）
3. 按 §6 自测验证
4. 可选：按 §7 补 pending 重新调度（与幂等配合）

---

## 关联文档

- [组卷与导出功能设计](../组卷与导出功能设计.md)
- [接口幂等与防重复提交](../接口幂等与防重复提交.md)
- [事务提交后触发异步任务最佳实践](../事务提交后触发异步任务最佳实践.md)
