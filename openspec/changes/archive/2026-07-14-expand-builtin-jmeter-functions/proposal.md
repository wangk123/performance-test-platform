## Why

函数库基建（JAR、只读 API、管理台展示、编辑器插入、分布式注入）已就绪，但内置实现仅有 `randomMobile`、`randomString` 两个示例，无法覆盖压测脚本中常见的中国业务造数与请求编码/摘要需求。现在扩展首批内置函数，可直接提升脚本编写效率，且不改变现有架构。

## What Changes

- 在 `jmeter-functions` 新增首批内置函数实现（一函数一 `AbstractFunction` 类）
- 同步更新 `functions.json`、SPI 注册与 `perftest-jmeter-functions.jar`
- 造数类：`randomIdCard`、`randomBankCard`、`randomName`、`randomEmail`（保留已有手机号/随机串）
- 编码/摘要类：`md5`、`sha256`、`base64Encode`、`base64Decode`、`urlEncode`
- 分类扩展为 `DATA` / `CODEC`；管理台与编辑器随元数据自动展示，无前端结构改造

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `jmeter-function-runtime`: 扩展平台内置自定义 Function 集合与行为约定（格式合法造数、CODEC 入参约定）
- `jmeter-function-registry`: 扩展 `functions.json` 元数据条目与分类（DATA / CODEC），与实现 key 对齐

## Impact

- **jmeter-functions**：新增约 9 个 Function 类、SPI、`functions.json`、单元测试
- **backend**：`jmeter-runtime/perftest-jmeter-functions.jar` 随构建更新；API/注册表加载逻辑不变
- **frontend**：无结构改动，列表与插入依赖现有只读 API
- **不包含**：HMAC/AES、JMeter 原生函数目录、管理台 CRUD、造数工厂联动
