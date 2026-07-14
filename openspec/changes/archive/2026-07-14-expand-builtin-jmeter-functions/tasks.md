## 1. DATA 造数函数

- [x] 1.1 为 `randomIdCard` 编写失败单测（校验位/长度），再实现并通过
- [x] 1.2 为 `randomBankCard` 编写失败单测（Luhn），再实现并通过
- [x] 1.3 为 `randomName`、`randomEmail` 编写失败单测（非空、邮箱含 `@`），再实现并通过
- [x] 1.4 将新 DATA 函数类注册到 `META-INF/services/org.apache.jmeter.functions.Function`

## 2. CODEC 编码/摘要函数

- [x] 2.1 为 `md5`、`sha256` 编写失败单测（固定输入 → 小写 hex），再实现并通过
- [x] 2.2 为 `base64Encode` / `base64Decode` 编写失败单测（往返），再实现并通过
- [x] 2.3 为 `urlEncode` 编写失败单测（含非 ASCII），再实现并通过
- [x] 2.4 将新 CODEC 函数类注册到 SPI

## 3. 元数据与对齐校验

- [x] 3.1 更新 `jmeter-functions/.../functions.json`：补齐 11 个 key，`DATA`/`CODEC` 分类与参数/示例
- [x] 3.2 同步 backend 侧 `functions.json`（若构建未自动覆盖）
- [x] 3.3 增加单测：`functions.json` 每个 key 对应已注册 `getReferenceKey()`

## 4. 打包与回归

- [x] 4.1 构建并更新 `backend/src/main/resources/jmeter-runtime/perftest-jmeter-functions.jar`
- [x] 4.2 运行 `jmeter-functions` 与相关 backend registry 测试，确认列表含全部首批 key
- [x] 4.3 按需更新 `docs/modules/08-function-library.md` 中的内置函数说明（若文档仍只列示例）
