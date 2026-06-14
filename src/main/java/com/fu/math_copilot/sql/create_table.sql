

-- 创建库
create database if not exists math_copilot;

-- 切换库
use math_copilot;

-- 用户表
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           not null comment '账号',
    userPassword varchar(512)                           not null comment '密码',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin/ban',
    editTime     datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    index idx_Id (id)
) comment '用户' collate = utf8mb4_unicode_ci;

-- 题库表
create table if not exists question_bank
(
    id          bigint auto_increment comment 'id' primary key,
    title       varchar(256)                       null comment '标题',
    description text                               null comment '描述',
    picture     varchar(2048)                      null comment '图片',
    userId      bigint                             not null comment '创建用户 id',
    editTime    datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime  datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime  datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete    tinyint  default 0                 not null comment '是否删除',
    index idx_title (title)
) comment '题库' collate = utf8mb4_unicode_ci;

-- 知识点目录树
create table if not exists knowledge_point
(
    id         bigint auto_increment comment 'id' primary key,
    parentId   bigint       default 0                 not null comment '父节点 id，0 表示根',
    name       varchar(128)                           not null comment '知识点名称',
    path       varchar(512)                           not null comment '物化路径，如 /1/3/7/',
    level      tinyint                                not null comment '层级',
    sort       int          default 0                 not null comment '同级排序',
    createTime datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete   tinyint      default 0                 not null comment '是否删除',
    index idx_parentId (parentId),
    index idx_path (path(191))
) comment '知识点目录树' collate = utf8mb4_unicode_ci;

-- 题目表（数学题：题干/答案/解析存 LaTeX 文本）
create table if not exists question
(
    id           bigint auto_increment comment 'id' primary key,
    content      text                                   not null comment '题干（LaTeX）',
    questionType varchar(32)                            not null comment '题型：single/multiple/judge/blank/subjective',
    difficulty   tinyint      default 3                 not null comment '难度 1-5',
    options      text                                   null comment '客观题选项 JSON 字符串',
    answer       text                                   not null comment '标准答案（LaTeX 或选项 key）',
    analysis     text                                   null comment '解析（LaTeX）',
    source       varchar(256)                           null comment '题目来源',
    picture      varchar(2048)                          null comment '配图 URL',
    userId       bigint                                 not null comment '创建用户 id',
    editTime     datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    index idx_userId (userId),
    index idx_questionType (questionType),
    index idx_difficulty (difficulty)
) comment '题目' collate = utf8mb4_unicode_ci;

-- 题目-知识点关联（多对多）
create table if not exists question_knowledge_point
(
    id                 bigint auto_increment comment 'id' primary key,
    questionId         bigint                             not null comment '题目 id',
    knowledgePointId   bigint                             not null comment '知识点 id',
    userId             bigint                             not null comment '关联创建用户 id',
    createTime         datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime         datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    UNIQUE KEY uk_question_kp (questionId, knowledgePointId),
    index idx_knowledgePointId (knowledgePointId)
) comment '题目知识点关联' collate = utf8mb4_unicode_ci;

-- 题库题目表（硬删除）
create table if not exists question_bank_question
(
    id             bigint auto_increment comment 'id' primary key,
    questionBankId bigint                             not null comment '题库 id',
    questionId     bigint                             not null comment '题目 id',
    userId         bigint                             not null comment '创建用户 id',
    createTime     datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime     datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    UNIQUE (questionBankId, questionId)
) comment '题库题目' collate = utf8mb4_unicode_ci;

-- 用户题目做题状态（不存作答内容，用户手动更新状态）
create table if not exists user_question_status
(
    id         bigint auto_increment comment 'id' primary key,
    userId     bigint       not null comment '用户 id',
    questionId bigint       not null comment '题目 id',
    status     varchar(32)  not null default 'not_done' comment 'not_done/pending/done',
    result     varchar(16)  null comment 'correct/wrong，仅 status=done 时有值',
    createTime datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    UNIQUE KEY uk_user_question (userId, questionId),
    index idx_userId_status (userId, status)
) comment '用户题目做题状态' collate = utf8mb4_unicode_ci;

-- 题目收藏（硬删除）
create table if not exists question_favorite
(
    id         bigint auto_increment comment 'id' primary key,
    userId     bigint not null comment '用户 id',
    questionId bigint not null comment '题目 id',
    createTime datetime default CURRENT_TIMESTAMP not null comment '收藏时间',
    UNIQUE KEY uk_user_question (userId, questionId),
    index idx_userId (userId)
) comment '题目收藏' collate = utf8mb4_unicode_ci;

-- 试卷（组卷实例，逻辑删除）
create table if not exists exam_paper
(
    id            bigint auto_increment comment 'id' primary key,
    title         varchar(256)                       not null comment '试卷标题',
    userId        bigint                             not null comment '组卷用户 id',
    generateType  varchar(32)                        not null comment '组卷方式：manual/knowledge/random',
    conditionJson text                               null comment '组卷条件快照(JSON)',
    questionCount int      default 0                 not null comment '题目数量',
    totalScore    int      default 0                 not null comment '总分',
    requestNo     varchar(64)                        null comment '幂等业务号',
    createTime    datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime    datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete      tinyint  default 0                 not null comment '是否删除',
    UNIQUE KEY uk_requestNo (requestNo),
    index idx_userId (userId)
) comment '试卷(组卷实例)' collate = utf8mb4_unicode_ci;

-- 试卷题目（只存引用与排版）
create table if not exists exam_paper_question
(
    id          bigint auto_increment comment 'id' primary key,
    paperId     bigint                             not null comment '试卷 id',
    questionId  bigint                             not null comment '题目 id',
    sortNo      int      default 0                 not null comment '题序',
    score       int      default 0                 not null comment '分值',
    sectionType varchar(32)                        null comment '题型分区',
    createTime  datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    UNIQUE KEY uk_paper_question (paperId, questionId),
    index idx_paperId (paperId)
) comment '试卷题目' collate = utf8mb4_unicode_ci;

-- 导出任务（异步）
create table if not exists export_task
(
    id            bigint auto_increment comment 'id' primary key,
    paperId       bigint                             not null comment '试卷 id',
    userId        bigint                             not null comment '发起用户 id',
    format        varchar(16)                        not null comment '导出格式：pdf/docx/html',
    contentScope  varchar(32) default 'question'     not null comment 'question/answer/both',
    status        varchar(16) default 'pending'      not null comment 'pending/processing/success/failed',
    progress      tinyint     default 0              not null comment '进度 0-100',
    fileUrl       varchar(1024)                      null comment 'COS 下载地址',
    fileKey       varchar(512)                       null comment 'COS 对象 key',
    errorMsg      varchar(512)                       null comment '失败原因',
    snapshotJson  mediumtext                         null comment '导出成功时定格的题面 JSON',
    idempotentKey varchar(64)                        null comment '幂等键',
    createTime    datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime    datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    UNIQUE KEY uk_idempotent (idempotentKey),
    index idx_paperId (paperId),
    index idx_userId_status (userId, status)
) comment '导出任务' collate = utf8mb4_unicode_ci;
