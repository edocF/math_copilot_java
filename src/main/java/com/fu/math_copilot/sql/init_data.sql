-- 知识点树初始数据（高中数学示例）
INSERT INTO knowledge_point (id, parentId, name, path, level, sort)
VALUES (1, 0, '高中数学', '/1/', 1, 1),
       (2, 1, '代数', '/1/2/', 2, 1),
       (3, 2, '函数', '/1/2/3/', 3, 1),
       (4, 3, '二次函数', '/1/2/3/4/', 4, 1),
       (5, 1, '几何', '/1/5/', 2, 2),
       (6, 5, '三角形', '/1/5/6/', 3, 1),
       (7, 2, '方程', '/1/2/7/', 3, 2),
       (8, 1, '概率统计', '/1/8/', 2, 3);

-- 题库表初始数据（数学专项）
INSERT INTO question_bank (title, description, picture, userId)
VALUES ('函数专项', '函数与二次函数相关练习', 'https://www.mianshiya.com/logo.png', 1),
       ('几何基础', '三角形与勾股定理', 'https://www.mianshiya.com/logo.png', 1),
       ('方程与不等式', '一元二次方程练习', 'https://www.mianshiya.com/logo.png', 2),
       ('概率入门', '古典概型基础题', 'https://www.mianshiya.com/logo.png', 1);

-- 题目表初始数据（无 title，题干为 content）
INSERT INTO question (content, questionType, difficulty, options, answer, analysis, source, userId)
VALUES ('已知函数 $f(x)=x^2-4x+3$，求其最小值。', 'subjective', 3, NULL, '$-1$',
        '配方：$f(x)=(x-2)^2-1$，当 $x=2$ 时取最小值 $-1$。', '教材例题', 1),
       ('下列方程中，有两个不相等实数根的是（  ）', 'single', 2,
        '[{"key":"A","content":"$x^2+1=0$"},{"key":"B","content":"$x^2-4x+4=0$"},{"key":"C","content":"$x^2-3x+2=0$"},{"key":"D","content":"$x^2+x+1=0$"}]',
        'C', '判别式 $\\Delta=b^2-4ac$：C 中 $\\Delta=1>0$。', '高考模拟', 1),
       ('在 $\\triangle ABC$ 中，若 $\\angle C=90^\\circ$，$AC=3$，$BC=4$，则 $AB=$____。', 'blank', 2, NULL, '$5$',
        '勾股定理：$AB=\\sqrt{3^2+4^2}=5$。', '教材', 1),
       ('命题「对顶角相等」是真命题。', 'judge', 1, NULL, 'true', '对顶角相等是平面几何基本定理。', '教材', 2),
       ('解方程 $x^2-5x+6=0$。', 'subjective', 2, NULL, '$x_1=2,\\; x_2=3$',
        '因式分解：$(x-2)(x-3)=0$。', '教材', 1),
       ('从 1,2,3,4,5 中任取两数，和为偶数的概率是（  ）', 'single', 3,
        '[{"key":"A","content":"$\\\\dfrac{2}{5}$"},{"key":"B","content":"$\\\\dfrac{3}{5}$"},{"key":"C","content":"$\\\\dfrac{4}{10}$"},{"key":"D","content":"$\\\\dfrac{1}{2}$"}]',
        'A', '两数同奇偶：$C_3^2+C_2^2=4$，共 $C_5^2=10$ 种，概率 $\\dfrac{4}{10}=\\dfrac{2}{5}$。', '模拟卷', 2),
       ('函数 $y=\\dfrac{1}{x-1}$ 的定义域为____。', 'blank', 2, NULL, '$x\\neq 1$ 或 $(-\\infty,1)\\cup(1,+\\infty)$',
        '分母不为零：$x-1\\neq 0$。', '练习册', 1),
       ('下列函数中，在 $(0,+\\infty)$ 上单调递增的是（  ）', 'single', 3,
        '[{"key":"A","content":"$y=-x+1$"},{"key":"B","content":"$y=x^2$"},{"key":"C","content":"$y=\\\\dfrac{1}{x}$"},{"key":"D","content":"$y=2-x$"}]',
        'B', '$y=x^2$ 在 $(0,+\\infty)$ 单调递增。', '模拟卷', 2),
       ('已知 $\\sin\\theta=\\dfrac{3}{5}$，且 $\\theta$ 为锐角，求 $\\cos\\theta$。', 'subjective', 4, NULL, '$\\dfrac{4}{5}$',
        '$\\cos^2\\theta=1-\\sin^2\\theta=\\dfrac{16}{25}$，锐角取正。', '高考真题', 1),
       ('不等式 $2x-1>3$ 的解集为____。', 'blank', 1, NULL, '$x>2$',
        '$2x>4 \\Rightarrow x>2$。', '教材', 1);

-- 题目-知识点关联
INSERT INTO question_knowledge_point (questionId, knowledgePointId, userId)
VALUES (1, 4, 1),
       (2, 7, 1),
       (3, 6, 1),
       (4, 6, 2),
       (5, 7, 1),
       (6, 8, 2),
       (7, 3, 1),
       (8, 3, 2),
       (9, 3, 1),
       (10, 7, 1);

-- 题库题目关联
INSERT INTO question_bank_question (questionBankId, questionId, userId)
VALUES (1, 1, 1),
       (1, 2, 1),
       (1, 7, 1),
       (1, 8, 1),
       (1, 9, 1),
       (2, 3, 1),
       (2, 4, 1),
       (3, 5, 1),
       (3, 10, 1),
       (4, 6, 2);

-- 用户题目做题状态示例（用户 1）
INSERT INTO user_question_status (userId, questionId, status, result)
VALUES (1, 1, 'done', 'correct'),
       (1, 2, 'pending', NULL),
       (1, 3, 'done', 'wrong');

-- 题目收藏示例
INSERT INTO question_favorite (userId, questionId)
VALUES (1, 1),
       (1, 5),
       (2, 3);
