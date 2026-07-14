# 函数库模块

## 1. 模块定位

函数库提供平台维护的 JMeter 自定义 Java Function，供脚本 HTTP 组件以 `${__funcName(...)}` 语法引用。管理台只读展示与插入，函数在仓库 `jmeter-functions` 子模块中扩展。

## 2. 职责边界

| 负责 | 不负责 |
|------|--------|
| Java Function 实现与发版 | 管理台增删改函数 |
| 函数元数据查询与 JAR 下载 | Groovy 在线编辑与调试 |
| 分布式执行时注入 `lib/ext/` | 平台本机 JMeter 压测 |
| HTTP 编辑器函数插入 | HTTP 调试时执行函数 |

## 3. 功能范围

1. `jmeter-functions` 子模块构建 `perftest-jmeter-functions.jar`。
2. `functions.json` 维护展示元数据，与 Java 实现 key 对齐。
3. 只读 API：`GET /api/jmeter-functions`、`GET /api/jmeter-functions/download`。
4. 管理台函数库页列表展示、下载函数包、复制示例语法。
5. HTTP 编辑器 `VariablePanel`「平台函数」区点选插入。
6. 分布式执行自动将 `jmeter-runtime/*.jar` 拷贝至容器 `lib/ext/`。

## 4. 本地执行说明

平台压测仅走分布式 Docker 节点。若需单机执行：

1. 导出 JMX 脚本。
2. 从管理台下载 `perftest-jmeter-functions.jar`。
3. 放入本地 JMeter `lib/ext/` 后启动 JMeter。

## 5. 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/jmeter-functions` | 函数元数据列表 |
| `GET` | `/api/jmeter-functions/download` | 下载函数 JAR 包 |

## 6. 扩展函数

1. 在 `jmeter-functions` 新增 `AbstractFunction` 实现。
2. 注册 `META-INF/services/org.apache.jmeter.functions.Function`。
3. 同步更新 `functions.json`。
4. 发版后 JAR 随 `jmeter-runtime/` 注入分布式执行环境。

## 7. 内置函数（首批）

| key | 分类 | 说明 |
|-----|------|------|
| `randomMobile` | DATA | 随机中国大陆手机号 |
| `randomString` | DATA | 随机字母数字串 |
| `randomIdCard` | DATA | 随机身份证号（全国区划+有效日期+校验码，测试造数） |
| `randomBankCard` | DATA | 随机银行卡号（Luhn，测试造数） |
| `randomName` | DATA | 随机中文姓名 |
| `randomEmail` | DATA | 随机邮箱 |
| `md5` | CODEC | MD5 小写 hex |
| `sha256` | CODEC | SHA-256 小写 hex |
| `base64Encode` / `base64Decode` | CODEC | Base64 编解码（UTF-8 文本） |
| `urlEncode` | CODEC | URL 编码 |

调用语法：`${__key(...)}`。JMeter 原生函数（如 `__UUID`、`__time`）不在本 JAR 内。
