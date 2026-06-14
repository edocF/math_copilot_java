<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="utf-8"/>
    <title>${paper.title!""}</title>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.css"/>
    <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.js"></script>
    <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/contrib/auto-render.min.js"></script>
    <style>
        body { font-family: "Microsoft YaHei", sans-serif; margin: 40px; color: #222; line-height: 1.6; }
        h1 { text-align: center; margin-bottom: 8px; }
        .meta { text-align: center; color: #666; margin-bottom: 32px; }
        .question { margin-bottom: 20px; page-break-inside: avoid; }
        .question-stem { display: inline; }
        .question .no { font-weight: bold; margin-right: 6px; }
        .options { margin: 10px 0 0 24px; }
        .option-item { margin-bottom: 6px; }
        .option-key { font-weight: bold; margin-right: 6px; }
        .judge-bracket { margin-left: 8px; letter-spacing: 4px; }
        .blank-answer { margin: 10px 0 0 24px; }
        .blank-line {
            display: inline-block;
            min-width: 180px;
            border-bottom: 1px solid #333;
            vertical-align: bottom;
        }
        .subjective-space { height: 120px; margin-top: 10px; }
        .page-break { page-break-before: always; margin-top: 40px; }
        .answer-item { margin-bottom: 16px; }
        img { max-width: 100%; }
    </style>
</head>
<body>
<h1>${paper.title!""}</h1>
<div class="meta">共 ${paper.questionCount!0} 题 / ${paper.totalScore!0} 分</div>

<#list questions![] as q>
    <div class="question">
        <div class="question-stem">
            <span class="no">${q.sortNo!""}.</span>
            <span class="content">${q.content!""}</span>
            <#if q.questionType?? && q.questionType == "judge">
                <span class="judge-bracket">（&nbsp;&nbsp;&nbsp;&nbsp;）</span>
            </#if>
        </div>
        <#if q.picture?? && q.picture?has_content>
            <div><img src="${q.picture}" alt="配图"/></div>
        </#if>

        <#if q.optionList?? && (q.optionList?size > 0)>
            <div class="options">
                <#list q.optionList as opt>
                    <div class="option-item">
                        <span class="option-key">${(opt.key)!""}.</span>
                        <span class="option-content">${opt.content!""}</span>
                    </div>
                </#list>
            </div>
        </#if>

        <#if q.questionType?? && q.questionType == "blank">
            <div class="blank-answer">答：<span class="blank-line">&nbsp;</span></div>
        </#if>

        <#if q.questionType?? && q.questionType == "subjective">
            <div class="subjective-space"></div>
        </#if>
    </div>
</#list>

<#if showAnswer>
    <div class="page-break"></div>
    <h2>参考答案与解析</h2>
    <#list questions![] as q>
        <div class="answer-item">
            <strong>${q.sortNo!""}.</strong>
            答案：${q.answer!""}
            <#if q.analysis?? && q.analysis?has_content>
                <br/>解析：${q.analysis!""}
            </#if>
        </div>
    </#list>
</#if>

<script>
    (function () {
        function renderAndMarkReady() {
            if (typeof renderMathInElement === "function") {
                renderMathInElement(document.body, {
                    delimiters: [
                        {left: "$$", right: "$$", display: true},
                        {left: "$", right: "$", display: false},
                        {left: "\\(", right: "\\)", display: false},
                        {left: "\\[", right: "\\]", display: true}
                    ],
                    throwOnError: false
                });
            }
            document.body.setAttribute("data-katex-ready", "true");
        }

        function waitForKaTeX() {
            if (typeof renderMathInElement === "function") {
                renderAndMarkReady();
                return;
            }
            setTimeout(waitForKaTeX, 100);
        }

        if (document.readyState === "loading") {
            document.addEventListener("DOMContentLoaded", waitForKaTeX);
        } else {
            waitForKaTeX();
        }
    })();
</script>
</body>
</html>
