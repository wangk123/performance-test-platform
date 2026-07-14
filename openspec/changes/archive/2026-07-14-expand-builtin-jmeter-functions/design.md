## Context

`jmeter-functions` 基建已完成：`AbstractFunction` + SPI、`functions.json` 只读 API、管理台列表、编辑器插入、分布式 `lib/ext` 注入均可用。当前仅实现 `randomMobile`、`randomString` 两个示例。本变更在既有模式上扩展首批内置函数，不改 API/UI 架构。

约束：Apache JMeter 5.6.3；一函数一类；与现有 `RandomMobile` / `RandomString` 风格对齐；不新增外部依赖。

## Goals / Non-Goals

**Goals:**

- 提供首批约 11 个平台函数（含已有 2 个）：中国业务造数 + 编码/摘要
- `functions.json` / SPI / Java 实现 key 同 PR 一致，单测校验
- 构建产物更新 `perftest-jmeter-functions.jar` 至 `jmeter-runtime/`

**Non-Goals:**

- HMAC、AES、业务专用签名函数
- JMeter 原生函数（`__UUID`、`__time` 等）目录展示
- 管理台 CRUD、HTTP 调试执行函数
- 抽取共用 Generator 工具层（规模扩大后再考虑）
- 造数工厂模块联动

## Decisions

### 1. 一函数一 `AbstractFunction` 类

沿用现有模块结构，每个 key 独立类 + SPI 一行注册。

**理由**：与现状一致，review 清晰，首批规模小。  
**备选**：公共工具类 + 薄壳 — 造数规则复杂后再引入。

### 2. 首批函数清单与约定

| key | category | 参数 | 行为 |
|-----|----------|------|------|
| `randomMobile` | DATA | 无 | 已有 |
| `randomString` | DATA | length（可选，默认 8） | 已有 |
| `randomIdCard` | DATA | 无 | 18 位；全国县级区划代码池；有效出生日期；GB 11643 校验码 |
| `randomBankCard` | DATA | 无 | 含 Luhn，固定样例 BIN |
| `randomName` | DATA | 无 | 常见姓+名池 |
| `randomEmail` | DATA | 无 | 随机本地部分 + 域名池 |
| `md5` | CODEC | text（必填） | UTF-8 → 小写 hex |
| `sha256` | CODEC | text（必填） | UTF-8 → 小写 hex |
| `base64Encode` | CODEC | text（必填） | UTF-8 → Base64 |
| `base64Decode` | CODEC | text（必填） | Base64 → UTF-8 字符串 |
| `urlEncode` | CODEC | text（必填） | UTF-8 URL encode |

造数保证**格式合法**（身份证校验码、银行卡 Luhn），不保证可过真实业务/银行校验。  
CODEC 缺参或非法输入：与现有函数一致，`setParameters` / `execute` 抛 `InvalidVariableException` 或返回可预期失败行为（实现时与模块现有测试惯例对齐）。

命名：`getReferenceKey()` 与 `functions.json` 的 `key` 使用 camelCase（无 `__` 前缀），调用语法 `${__key(...)}`，与现有实现一致。

### 3. 元数据同步策略

同 PR 更新：`*.java`、`META-INF/services/...Function`、`functions.json`（模块与 backend 资源副本若构建未自动同步则手动/构建同步）。单测断言：`functions.json` 的每个 `key` 在 SPI 注册的实现中存在对应 `getReferenceKey()`。

### 4. 不改前端与 API

列表与插入完全依赖现有 `GET /api/jmeter-functions`；新增 category 值由前端原样展示。

## Risks / Trade-offs

| 风险 | 缓解 |
|------|------|
| 身份证/银行卡被误用于真实业务校验 | 文档与 description 标明「测试造数，格式合法」 |
| `functions.json` 与实现不同步 | 单测校验 key 集合一致 |
| Base64 解码非 UTF-8 二进制 | 首版约定按 UTF-8 文本；description 说明用途为文本 |
| 函数变多后管理台无筛选 | 接受；后续可按 category 筛选，本变更不做 |

## Migration Plan

1. 实现新函数与测试 → 构建 JAR → 放入 `jmeter-runtime/`
2. 确认 API 返回完整列表
3. 无数据迁移；回滚即回退该 JAR 与源码版本

## Open Questions

（无；探索阶段已确认范围 A 与实现结构 A）
