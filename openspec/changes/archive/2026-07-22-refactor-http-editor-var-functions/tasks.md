## 1. Shared utilities

- [x] 1.1 扩展 `syntax-highlight.ts`：变量/函数调用高亮正则覆盖 `${var}` 与 `${__fn(...)}`，并为现有高亮路径补充单测（先写失败测试再实现）
- [x] 1.2 新增 JMeter 内置函数前端静态目录（常用子集：name / example / category / description），提供可被面板与补全复用的导出
- [x] 1.3 扩展请求组装/预览工具（复用 `http-debug` 变量替换约定）：输入当前 HTTP 配置 → 输出预览用 method/URL/headers/body；函数语法保持字面量；补单测

## 2. 右侧变量与函数面板

- [x] 2.1 重写 `VariablePanel`：分区展示系统/项目变量、平台函数、JMeter 内置函数；点击插入 example/占位符
- [x] 2.2 调整 `.http-config-layout` / `.variable-panel` 样式：面板高度受 HTTP 编辑区约束，列表区独立 `overflow-y: auto`，不再撑高整页

## 3. 编辑字段快捷引用与高亮

- [x] 3.1 打通 raw Body（`CodeEditor`）的 `activeField` / 插入管道，使右侧点选与 `${` 补全能写入 raw Body
- [x] 3.2 合并补全候选：变量 + 平台函数 example + 内置函数 example；覆盖 URL / Params / Headers / Body
- [x] 3.3 在 `VariableField` / KV 编辑器启用变量与函数高亮（去掉不必要的 `plain` 关闭）；raw Body 按 design 最小可行落地高亮或等价可见标记

## 4. 请求报文预览

- [x] 4.1 在 HTTP 配置区增加请求报文预览入口与只读展示（method / URL / headers / body）
- [x] 4.2 预览中替换已知变量、保留函数字面量，并提示「函数不在预览中执行」

## 5. 验证

- [x] 5.1 手动验证：各字段插入变量/两类函数、面板滚动、高亮、预览与现有 HTTP debug 行为未回退
- [x] 5.2 相关前端单测通过
