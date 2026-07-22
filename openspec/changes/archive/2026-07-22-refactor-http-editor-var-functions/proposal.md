## Why

脚本编辑器 HTTP 组件的变量/函数引用体验不完整：raw Body 无法从右侧插入，补全不含函数，右侧列表面板会撑高整页，且 `${__fn(...)}` 不高亮、缺少编辑态请求报文预览。现在重构该区域，把变量与函数（函数库 + JMeter 内置）变成可快捷引用、可滚动浏览、可预览的一等编辑能力。

## What Changes

- 在 URL、Headers、Params、Body（含 raw）等请求参数区域统一支持快捷引用变量与函数（`${` 补全 + 右侧点选插入）
- 函数列表同时覆盖：平台函数库（`GET /api/jmeter-functions`）与 JMeter 内置函数目录
- 重写 HTTP 右侧「变量与函数」面板：列表区域独立上下滚动，不再撑高整页
- 变量与函数语法在可编辑字段中高亮显示（含 `${var}` 与 `${__fn(...)}`）
- 新增编辑态「请求报文预览」：展示当前组装的 method/URL/headers/body（变量可替换预览；函数语法保持字面量，与现有 debug 约定一致）

## Capabilities

### New Capabilities

- `http-editor-var-functions`: HTTP 请求编辑器内的变量/函数快捷引用、右侧可滚动面板、语法高亮与请求报文预览

### Modified Capabilities

- `jmeter-function-ui`: 扩展 HTTP 编辑器函数插入范围——除平台函数外增加 JMeter 内置函数目录；补全与插入覆盖 raw Body 等全部请求参数字段

## Impact

- **frontend**：`HttpRequestConfig.vue`、`VariablePanel.vue`、`VariableField.vue`、`HttpKeyValueEditor.vue`、`HttpBodyConfig.vue`、`CodeEditor.vue`、`syntax-highlight.ts`、`http-request-config.ts`、相关样式
- **frontend data**：新增/维护 JMeter 内置函数静态目录（前端常量或本地 JSON，不依赖后端 JAR）
- **backend**：不改 API 契约；平台函数仍走现有 `GET /api/jmeter-functions`
- **不包含**：HTTP debug 真执行函数、函数库 CRUD、新后端 API
