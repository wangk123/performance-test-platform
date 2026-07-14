## Why

脚本 HTTP 组件需要可复用的业务函数（如随机手机号、签名），且压测执行要求低开销、与 JMeter 原生 `${__func()}` 语法兼容。平台采用 Java 自定义 JMeter Function 在代码中维护，管理台只读展示与插入，执行仅走分布式 Docker 节点；单机需求通过导出 JMX + 下载函数 JAR 自行执行。

## What Changes

- 新增 `jmeter-functions` Gradle 子模块，开发 `AbstractFunction` 实现，构建 `perftest-jmeter-functions.jar`
- 函数元数据由仓库内 `functions.json` 维护，随发版更新；**不提供**管理台增删改
- 新增只读 API：函数列表查询、函数包 JAR 下载
- 管理台「函数库」页：列表展示名称、分类、参数、示例语法；支持下载函数包
- HTTP 编辑器 `VariablePanel` 增加「平台函数」区，点选插入 `${__funcName(...)}`
- 分布式执行：泛化 `jmeter-runtime/*.jar` 任务级拷贝至容器 `lib/ext/`（沿用 HdrHistogram 模式）
- 明确产品边界：**平台不支持本机 JMeter 执行**；本地压测请导出脚本并安装函数 JAR
- 移除或废弃 `ModuleMockController` 函数 Mock 接口，由真实 API 替代

## Capabilities

### New Capabilities

- `jmeter-function-registry`: 函数元数据注册表、只读查询 API、函数包下载
- `jmeter-function-runtime`: 函数 JAR 构建打包与分布式执行时注入 `lib/ext`
- `jmeter-function-ui`: 管理台函数库只读页与 HTTP 编辑器函数插入

### Modified Capabilities

（无）

## Impact

- **后端**：新子模块、`JmeterFunctionController`、runtime JAR 打包、`DistributedJmeterExecutionRunner` / `remote-runner` 依赖拷贝逻辑
- **前端**：`ProjectDetail` 函数库页、`VariablePanel` / `HttpRequestConfig`、新 API 客户端
- **运维**：函数变更随平台发版；远程节点无需重建 Docker 镜像
- **文档**：更新函数库模块说明，明确不支持平台单机执行
