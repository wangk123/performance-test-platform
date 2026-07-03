## Context

函数库模块当前为占位状态：`ModuleMockController` 返回假数据，管理台仅提示文案。脚本 HTTP 编辑器已有 `VariablePanel` 支持 `${变量}` 插入，但无平台函数能力。

压测执行已固定走分布式 Docker（`justb4/jmeter:latest`），`JmeterCommandExecutor` 本机路径无调用方。HdrHistogram JAR 已通过 `jmeter-runtime/` + 任务级拷贝至容器 `lib/ext/` 的模式运行。

探索结论：采用 Java `AbstractFunction` 而非 Groovy CRUD，以降低运行时开销；函数在仓库代码中维护；管理台只读；平台不执行单机压测，用户导出 JMX + 函数 JAR 本地运行。

## Goals / Non-Goals

**Goals:**

- 提供可复用的 JMeter 自定义函数，语法 `${__funcName(...)}`，与 JMeter 5.6.3 兼容
- 函数元数据可查询、函数包可下载，供管理台展示与本地 JMeter 安装
- HTTP 编辑器可点选插入函数调用
- 分布式执行自动注入函数 JAR 至容器 `lib/ext/`

**Non-Goals:**

- 管理台函数增删改、在线调试、版本 CRUD
- Groovy 函数库、JSR223 函数注入
- 平台本机 JMeter 执行及对应 JAR 注入
- HTTP 调试时模拟 `${__func()}` 执行结果
- 自定义 Docker 镜像（继续运行时注入）

## Decisions

### 1. Java Function 子模块

新建 `jmeter-functions/` Gradle 子模块，依赖 `org.apache.jmeter:ApacheJMeter_core`（compileOnly，版本 5.6.3）。每个函数类 extends `AbstractFunction`，通过 `META-INF/services/org.apache.jmeter.functions.Function` 注册。

**理由**：性能最优、JMeter 原生语法。  
**备选**：Groovy CRUD — 灵活但开销大、实现复杂，已否决。

### 2. 元数据：`functions.json` 同仓维护

在 `jmeter-functions/src/main/resources/functions.json` 维护函数展示信息（name、displayName、category、parameters、example、description）。后端启动时加载，对外提供只读 API。

**理由**：简单可靠，与代码发版同步。  
**备选**：注解扫描生成 — 后续可演进，首版不引入。

### 3. JAR 打包与分发

构建产物 `perftest-jmeter-functions.jar` 拷贝至 `backend/src/main/resources/jmeter-runtime/`。与 HdrHistogram 一并作为 runtime 依赖。

分布式：`DistributedJmeterExecutionRunner` 写出所有 `jmeter-runtime/*.jar`；`remote-runner` 启动容器前 `cp /test/*.jar $JMETER_HOME/lib/ext/`（泛化现有 HdrHistogram 逻辑）。

**理由**：无需重建镜像，与现有链路一致。

### 4. 只读 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/jmeter-functions` | 函数列表（全局，非项目级） |
| GET | `/api/jmeter-functions/download` | 下载 `perftest-jmeter-functions.jar` |

删除 `ModuleMockController` 中 `/projects/{id}/functions` Mock。

### 5. 前端集成

- `ProjectDetail` 函数库 tab：表格展示 + 「下载函数包」按钮
- `VariablePanel` 第三区「平台函数」，数据来自 API，点击插入 `${__name(...)}`
- HTTP 调试保持现有 `${var}` 替换，不执行函数

### 6. 执行边界

平台压测仅 `ExecutionMode.DISTRIBUTED`。文档与 UI 说明：本地压测请导出 JMX 并安装函数 JAR。

## Risks / Trade-offs

| 风险 | 缓解 |
|------|------|
| 函数变更需发版 | 接受；由平台团队维护，文档说明发版节奏 |
| 用户本地 JMeter 未装 JAR 导致 `${__xxx}` 原样输出 | 管理台提供下载与安装说明；导出页可提示 |
| `functions.json` 与 Java 实现不同步 | Code review 要求同 PR 更新；可加单元测试校验 key 一致 |
| 多 JAR 拷贝至 lib/ext 冲突 | 命名空间规范 `__` 前缀；runtime 目录仅平台 JAR |
| HTTP 调试看不到函数结果 | UI 标注「调试不执行函数，请以分布式压测为准」 |

## Migration Plan

1. 实现子模块与示例函数，验证分布式执行可加载
2. 上线只读 API 与前端页面，替换 Mock
3. 更新 `docs/modules/08-function-library.md` 反映新方案
4. 无需数据迁移（无数据库表）

回滚：移除 runtime JAR 注入逻辑，恢复 Mock（不推荐长期）。

## Open Questions

- 首版示例函数清单（建议：`randomMobile`、`randomString` 各一个）
- 函数 API 是否需要项目级路径前缀（当前设计为全局 `/api/jmeter-functions`）
