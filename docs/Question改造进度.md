# Question 改造进度

## 本次目标

- 改造 `QuestionController` 相关接口与 `QuestionService` 相关方法
- **先不接 ES**，将 ES 查询能力临时停用
- 记录当前已完成与待完成项

---

## 已完成

### 1) Controller 改造

- 文件：`src/main/java/com/fu/interview_copilot/controller/QuestionController.java`
- 已完成：
  - 保留并适配 `add / update / delete / list / get` 主流程
  - `search/page/vo` 改为临时停用，直接抛出业务异常（ES 未开放）
  - 说明注释已补充：待 ES mapping 与同步链路完成后恢复

### 2) Service 改造

- 文件：`src/main/java/com/fu/interview_copilot/service/impl/QuestionServiceImpl.java`
- 已完成：
  - `validQuestion()`：
    - 增加 `questionType` 枚举合法性校验
    - 增加 `difficulty` 范围校验（1-5）
    - 客观题（single / multiple）要求 `options` 非空
    - 字段长度校验改为判空后再校验，避免空指针
  - `getQueryWrapper()`：
    - 去除 `title` 查询
    - 新增按 `questionType`、`difficulty`、`source` 查询
    - 模糊搜索改为 `content + answer`
    - 排序调用修正为 `orderBy(valid, asc, sortField)`
  - `searchFromEs()`：
    - 临时停用，抛出 `BusinessException(OPERATION_ERROR, "ES 搜索服务暂未开放")`

### 3) DTO / VO 对齐

- `QuestionAddRequest`：清理无用 import，保留数学题字段
- `QuestionQueryRequest`：移除 `title`，新增 `questionType / difficulty / source`
- `QuestionVO`：移除 `title`，补齐 `questionType / difficulty / options / analysis / source / picture`

### 4) Mapper 映射对齐

- 文件：`src/main/resources/mapper/QuestionMapper.xml`
- 已完成：
  - 移除 `title` 映射
  - `BaseResultMap` 与 `Base_Column_List` 对齐到新字段
  - 包含：`content, questionType, difficulty, options, answer, analysis, source, picture`

---

## 当前状态

- `Question` 主链路（增删改查）可继续联调
- ES 搜索接口已明确关闭，避免误用和错误数据路径
- 本次改动相关文件 lint 检查无新增报错

---

## 下一步建议

1. 新增/改造知识点关联接口（题目绑定 `question_knowledge_point`）
2. 增加 `questionType/difficulty/source` 的前端筛选联调
3. ES 恢复时再统一做：
   - `QuestionEsDto` 字段同步
   - `question_es_mapping.json` 字段同步
   - 增量/全量同步链路验证
