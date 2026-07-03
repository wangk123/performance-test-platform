## 1. jmeter-functions 子模块

- [x] 1.1 新建 `jmeter-functions/` Gradle 子模块，依赖 `ApacheJMeter_core` 5.6.3（compileOnly）
- [x] 1.2 实现示例函数（如 `RandomMobile`、`RandomString`）及 SPI 注册
- [x] 1.3 新增 `functions.json` 元数据，与实现函数 key 对齐
- [x] 1.4 构建任务将 `perftest-jmeter-functions.jar` 拷贝至 `backend/src/main/resources/jmeter-runtime/`

## 2. 后端只读 API

- [x] 2.1 实现 `JmeterFunctionRegistry` 加载 `functions.json`
- [x] 2.2 实现 `GET /api/jmeter-functions` 列表接口
- [x] 2.3 实现 `GET /api/jmeter-functions/download` JAR 下载接口
- [x] 2.4 移除 `ModuleMockController` 函数相关 Mock 端点
- [x] 2.5 添加 Registry 与 API 单元测试

## 3. 分布式 Runtime 注入

- [x] 3.1 扩展 `DistributedJmeterExecutionRunner`，写出所有 `jmeter-runtime/*.jar` 到执行目录
- [x] 3.2 泛化 `remote-runner` 启动脚本，拷贝 `/test/*.jar` 至 `$JMETER_HOME/lib/ext/`（非仅 HdrHistogram）
- [x] 3.3 验证分布式执行可解析 `${__randomMobile()}` 等函数调用

## 4. 前端函数库页

- [x] 4.1 新增 `api/jmeter-functions.ts` 客户端
- [x] 4.2 实现 `FunctionLibraryView` 只读列表页（替换 `ProjectDetail` 占位）
- [x] 4.3 添加「下载函数包」按钮与本地执行说明文案

## 5. HTTP 编辑器集成

- [x] 5.1 扩展 `VariablePanel` 增加「平台函数」区，数据来自 API
- [x] 5.2 点击函数插入 `${__funcName(...)}` 到当前 `VariableField`
- [x] 5.3 确认 HTTP 调试不执行函数（保持 `${var}` 替换行为）

## 6. 文档与收尾

- [x] 6.1 更新 `docs/modules/08-function-library.md` 反映 Java Function + 只读 + 分布式方案
- [x] 6.2 更新 README 函数库状态说明
