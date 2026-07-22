## Context

HTTP 请求编辑器已具备右侧 `VariablePanel`（系统变量 / 项目变量 / 平台函数）与 `VariableField` 的 `${` 补全，但 raw Body（`CodeEditor`）未接入插入管道，KV 字段关闭了高亮，函数调用语法 `${__fn(...)}` 无法匹配现有正则，右侧面板无独立滚动。平台函数来自 `GET /api/jmeter-functions`；JMeter 原生内置函数此前明确不在平台 JAR 中，编辑器也无目录。

约束：复用现有插入/补全状态机与 debug「不执行函数」约定；不新增后端 API；前端行数与最小改动纪律适用。

## Goals / Non-Goals

**Goals:**
- URL / Params / Headers / Body（form + raw）统一支持变量与函数快捷引用（补全 + 右侧插入）
- 右侧面板同时展示：变量、平台函数库、JMeter 内置函数；列表区独立滚动
- 字段内高亮 `${var}` 与 `${__fn(...)}`
- 编辑态请求报文预览（method + URL + headers + body）

**Non-Goals:**
- 预览或 debug 中真正执行 JMeter/平台函数
- 后端新增内置函数注册 API 或修改 JAR
- 函数库管理台 CRUD
- 非 HTTP 步骤组件的变量面板改造

## Decisions

### 1. JMeter 内置函数用前端静态目录

- **选择**：在 `frontend/src/` 维护精简的内置函数目录（name / example / category / description），与平台函数 API 结果在面板中分节展示。
- **理由**：原生函数不属于平台 JAR，无现成 API；静态目录可离线、零后端改动。
- **备选**：后端新增 `/api/jmeter-builtin-functions` — 拒绝，超出本变更范围且无运行时收益。
- **范围**：首批覆盖常用内置（如 `__UUID`、`__time`、`__Random`、`__threadNum`、`__V`、`__P`、`__property`、`__eval`、`__urlencode`、`__urldecode`、`__Base64Encode`/`Decode`、`__StringFromFile` 等常用项），不追求 JMeter 全量目录。

### 2. 统一 activeField 覆盖 raw Body

- **选择**：扩展 `ActiveVariableField` / 插入管道，使 `CodeEditor` 聚焦时也能接收右侧插入与 `${` 建议；插入走现有 `insertRaw`/`insertVariable`。
- **理由**：需求明确「Body 等请求参数区域」；现有缺口即 raw Body。
- **备选**：仅在 raw Body 旁放独立「插入」按钮 — 体验分裂，拒绝。

### 3. 高亮正则扩展为变量 + 函数调用

- **选择**：在 `syntax-highlight.ts` 将匹配从 `\$\{[\w.-]+\}` 扩展为可覆盖 `${__name(...)}`（允许括号与参数字符的保守正则），`VariableField` / KV / CodeEditor 高亮层共用。
- **理由**：单一工具函数，避免多处分叉。
- **注意**：JSON/XML 模式在原有高亮路径上叠加变量/函数 mark，不破坏语法着色。

### 4. 右侧面板布局：固定高度 + 内部滚动

- **选择**：`.http-config-layout` 右侧栏 `align-self: stretch` + `overflow: hidden`；`.variable-panel` 用 flex 列布局，分区标题固定，列表区域 `overflow-y: auto`（可按 section 内滚动或整面板滚动，优先整面板滚动 + sticky section 标题）。
- **理由**：直接解决「撑高整页」；改动集中在 CSS + 少量结构。
- **备选**：虚拟列表 — 当前条目量级不需要。

### 5. 请求报文预览为只读组装视图

- **选择**：在 HTTP 配置区增加「预览」入口（Tab 或侧栏折叠区），展示由当前 method/URL/headers/body 组装的报文文本；变量按现有 `http-debug` 替换逻辑做预览替换，函数语法保持字面量并提示「函数不在预览中执行」。
- **理由**：复用 debug 组装逻辑，不发请求；与现有「debug 不执行函数」一致。
- **备选**：仅在 debug 弹窗展示 — 不满足「编辑态预览」需求。

### 6. `${` 补全数据源合并

- **选择**：补全候选 = 系统/项目/脚本变量 + 平台函数 example + 内置函数 example（可按前缀过滤）。
- **理由**：与右侧目录同源，避免两套数据。

## Risks / Trade-offs

- [内置函数目录不全] → 文档化「常用子集」；后续可追加条目，不改 UI 结构
- [函数高亮误匹配复杂嵌套] → 采用非贪婪、单层括号正则；嵌套调用接受降级不高亮完整段
- [CodeEditor 高亮与 CodeMirror 冲突] → 优先装饰/overlay 或只读预览层；若成本过高，raw Body 以插入+预览为主、编辑态简化高亮，在 tasks 中按最小可行落地
- [面板双滚动条] → 仅列表区滚动，外层页面保持单滚动

## Migration Plan

- 纯前端变更，无数据迁移
- 回滚：还原相关 Vue/CSS/util 文件即可

## Open Questions

- 无阻塞问题。内置函数首批清单在实现 tasks 中按常用集落地，可按产品反馈增补。
