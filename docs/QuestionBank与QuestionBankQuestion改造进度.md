# QuestionBank 与 QuestionBankQuestion 改造进度

## 本次目标

- 检查 `QuestionBankController` 与 `QuestionBankQuestionController` 是否需要适配当前数学题改造
- 在「尽量少改」前提下完成必要修正

---

## 结论

这两块确实**不需要大规模接口改造**，主体逻辑仍可复用；本次主要做了两类必要修复：

1. 空题库详情查询的稳健性处理
2. 清理控制器中的无用依赖，降低噪声与潜在误导

---

## 已完成改动

## 1) `QuestionBankController`

文件：`src/main/java/com/fu/interview_copilot/controller/QuestionBankController.java`

- `getQuestionBankVO` 增加 `id` 合法性校验（`null` / `<=0`）
- 处理 `questionIds` 为空的情况，避免 `in ()` 引发 SQL 风险：
  - 由 `in(questionIds)` 改为  
    `in(questionIds.isEmpty() ? Collections.singleton(-1L) : questionIds)`
  - 空题库将稳定返回空分页数据

说明：接口签名保持不变，不影响前端调用。

## 2) `QuestionBankQuestionController`

文件：`src/main/java/com/fu/interview_copilot/controller/QuestionBankQuestionController.java`

- 删除未使用 import：
  - `QuestionBank`
  - `UserService`
  - `org.apache.poi.ss.formula.functions.T`

说明：仅代码整洁性修复，接口行为不变。

---

## 当前接口状态

### `QuestionBankController`

- `POST /questionBank/get/vo` ✅ 可用（已增强空题库场景）
- `POST /questionBank/list/page/vo` ✅ 可用
- `POST /questionBank/list/page` ✅ 可用（admin）
- `POST /questionBank/add` ✅ 可用（admin）
- `POST /questionBank/update` ✅ 可用（admin）
- `POST /questionBank/delete` ✅ 可用（admin）

### `QuestionBankQuestionController`

- `POST /questionBankQuestion/list/page` ✅ 可用（admin）
- `POST /questionBankQuestion/add` ✅ 可用（admin）
- `POST /questionBankQuestion/update` ✅ 可用（admin）
- `POST /questionBankQuestion/delete` ✅ 可用（admin）
- `POST /questionBankQuestion/batch/add` ✅ 可用（admin）
- `POST /questionBankQuestion/batch/delete` ✅ 可用（admin）

---

## 后续建议（可选，不在本次范围）

1. 题库详情接口可考虑返回 `Page<QuestionVO>`（而非 `Page<Question>`），统一展示层字段
2. `questionBankQuestion` 批量接口可增加单次上限校验（如 200）防止滥用
3. 题库删除时可明确是否级联清理关联（当前逻辑依赖业务侧处理）
