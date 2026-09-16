# P1-5 / P1-7 脚本模块功能完善 · 详细设计

> 2026-09-16 头脑风暴定稿（两项同属脚本模块，合并一份设计）。权威来源：`docs/architecture-and-roadmap.md` P1-5 / P1-7 行、D17（Groovy 优先、密钥参数化引用不落明文）、D14（执行明细不迁库，维持按执行分文件）。
> 无前置依赖。互相独立可分别实施；P1-7 的密钥参数化三条路之一是 CSV 引用（P1-5）。
> 用户已确认决策：CSV 绑定模型（§3.1 C2）、100MB 上限、新增 groovy 语言包、明文密钥扫描+警告。

## 1. 目标与验收

- **P1-5**：上传一个 CSV，配置到场景，多节点执行时每个节点都拿到该文件。
- **P1-7**：编辑器添加带请求签名的 JSR223 PreProcessor，导出 JMX 在 JMeter 与分布式执行中均生效。

## 2. 脚本模块现状与断点（调研结论）

1. 脚本真身 = 磁盘 JMX 文件（`script_versions` 只存元数据），结构模型是解析出的步骤树 `ScriptStepDefinition`；读写走 `JmeterScriptParser` / `JmeterScriptRenderer` / `JmeterScriptPatcher`（按 stepId DOM diff），装配唯一 seam 在 `ExecutionScriptAssembler`。
2. `CSV_DATA` 已是步骤类型，但只有 `fileName`/`variableNames` 两裸串——无文件资产、无版本、无完整 CSVDataSet 属性。
3. **分布式 CSV 到不了 worker**：`JmeterDependencyCollector` 能把「执行目录内、相对路径引用」的文件收进 payload，controller 全量收 dependencies，但 remote-runner 的 `runtime_jar_sources` 只把 `.jar` 推给 worker（`main.py:470-476`）；JMeter server 模式经 RMI 只传测试计划不传数据文件。worker 容器与 controller 同样挂载 `{remote_dir}:/test`（`main.py:310-317`），推文件到 worker 的 remote_dir 即可被读到。
4. `ScriptStepType` 已预留 `JSR223_PRE/POST_PROCESSOR`，渲染/解析/DOM/前端全未实现（渲染走 default 分支抛 unsupported）；`JmeterBackendListenerInjector` 注入内联 Groovy `<JSR223Listener>` 是现成先例。
5. **`${__P()}` 属性透传是断的**：场景 `jmeterProperties` 存入 `config_json`（`ScenarioExecutionService.java:145,161`），但 `DistributedJmeterExecutionRunner.payload()` 从不放进 payload，remote-runner 的 `-J` 全是写死的——D17 密钥参数化第一条路走不通，随 P1-7 修复。
6. 场景↔脚本步骤联动已有成熟模式：线程组预设 `ScenarioThreadGroupConfig(stepId, ...)` 随场景档案提交、服务端 `configSupport.normalize` 按脚本解析步骤校验、存 `task_scenarios.thread_group_configs_json`（`TaskScenarioService.applyScenarioProfile:122-125`）。
7. 前端：脚本编辑器 = `ScriptWorkspace` + `StepSidebar`/`StepDetail`/`StepCreateDialog`；CodeMirror（`CodeEditor.vue`）现支持 json/xml/html/javascript；`${` 函数联想与 `VariablePanel` 插入面板可复用。

---

## 3. P1-5 CSV 测试数据管理

### 3.1 决策记录

| # | 决策 | 内容 |
|---|------|------|
| C1 | 资产模型 | **项目级「数据文件 + 版本子表」实体**（仿 `aux_scripts` 模式）：`data_files` + `data_file_versions`，文件本体落 `storage/datafiles/{projectId}/df{id}/v{n}-{originalFilename}` |
| C2 | 绑定位置（**用户定制方案**） | **场景级绑定、计划文档侧操作**：场景关联脚本后，场景配置展示脚本解析出的 CSV 步骤，逐步骤下拉选择项目数据文件。绑定存 `task_scenarios.data_file_bindings_json`（`[{stepId, stepName, dataFileId}]`），完全仿线程组预设的 stepId 联动（前端随场景档案提交 → 服务端 normalize 按脚本步骤校验 → JSON 列存储）。**脚本步骤不存 dataFileId**——脚本项目级共享，绑进脚本会锁死数据来源；同一脚本在不同场景可用不同数据 |
| C3 | 版本语义 | **浮动 latest**：同名数据文件再上传 = 版本 +1，下次执行自动用新版。执行目录落 `data-files.json` manifest（stepId / dataFileId / versionNo / sha256 / fileName）供审计；不做绑定级钉定版本（YAGNI） |
| C4 | fileName 归属 | fileName 归**脚本步骤**所有（用户可读相对名，默认原始文件名风格）；执行装配按绑定把数据文件**内容**复制到执行目录/{步骤 fileName}，JMX 引用不变——导出 JMX 到本地 JMeter 仍可用。装配前校验脚本内 CSV fileName 无重复，冲突显式报错。兜底：未绑定步骤 / 导入 JMX 的裸 fileName，装配时若执行目录无此文件，按原始文件名**唯一匹配**项目数据文件补发（歧义或无匹配则跳过，维持现状行为） |
| C5 | 分发机制 | **修复 remote-runner worker 分发**：`start_worker` 从只收 `.jar` 改为接收全部 dependencies（与 controller 一致 SFTP 到 `{remoteWorkDir}/{runId}/`）。依赖收集器、payload 结构不动 |
| C6 | 上传限制（用户确认） | 数据文件单文件上限 **100MB**（新配置 `platform.datafile.max-size`）；实现方式 = 全局 `spring.servlet.multipart.max-file-size` 调至 100MB，截图上传接口在 controller 层自行维持 5MB 校验（红线语义不变）。上传时流式解析表头 + 计行数/sha256 存版本记录；不做压缩/分块/断点传输（内网 YAGNI） |
| C7 | CSVDataSet 属性补齐 | 补齐 `delimiter` / `fileEncoding` / `ignoreFirstLine` / `recycle` / `stopThread` / `shareMode` 的渲染与解析往返（现状只往返 fileName/variableNames），与 JMeter CSVDataSet 标准属性对齐 |
| C8 | 造数工厂导出 | **不做**（造数工厂明确"只写库、不导出参数文件"）；版本 `remark` 字段预留来源标注，衔接留待后期 |
| C9 | 删除语义 | 数据文件硬删除（记录 + 文件级联），与脚本版本一致；场景绑定引用已删文件时，执行装配显式报错并指出步骤名 |

### 3.2 数据模型（V10 迁移）

```sql
CREATE TABLE data_files (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id  BIGINT NOT NULL,
  name        VARCHAR(200) NOT NULL,
  remark      VARCHAR(500) NULL,
  created_by  VARCHAR(64) NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_data_file_name UNIQUE (project_id, name)
);

CREATE TABLE data_file_versions (
  id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
  data_file_id         BIGINT NOT NULL,
  version_no           INT NOT NULL,
  original_filename    VARCHAR(255) NOT NULL,
  stored_path          VARCHAR(500) NOT NULL,
  size_bytes           BIGINT NOT NULL,
  row_count            BIGINT NULL,
  header_columns_json  TEXT NULL,
  sha256               CHAR(64) NOT NULL,
  uploaded_by          VARCHAR(64) NULL,
  uploaded_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  remark               VARCHAR(500) NULL,
  CONSTRAINT uk_data_file_version UNIQUE (data_file_id, version_no)
);

ALTER TABLE task_scenarios ADD COLUMN data_file_bindings_json TEXT NULL;
```

- `name` 项目内唯一（追加版本的匹配键）；`version_no` = 现有版本数 + 1（与 `aux_scripts` 同法）；
- `header_columns_json` = `["colA","colB"]`，上传时按指定编码读首行；无表头存 `null`；
- 实体放 `com.yr.perftest.platform.datafile` 包；场景侧绑定 record `ScenarioDataFileBinding(stepId, stepName, dataFileId)` 与 `ScenarioThreadGroupConfig` 同居 `task` 包。

### 3.3 API

| 端点 | 说明 |
|------|------|
| `POST /api/projects/{projectId}/data-files` | multipart：`file` + `name` + `hasHeader` + `encoding`（默认 UTF-8）+ `remark?`。name 已存在则追加版本，否则新建；返回版本详情（表头/行数/sha256） |
| `GET /api/projects/{projectId}/data-files` | 列表：name、最新版本号、大小、行数、表头、更新时间 |
| `GET /api/projects/{projectId}/data-files/{id}/versions` | 版本列表 |
| `GET /api/projects/{projectId}/data-files/{id}/versions/{versionNo}` | 版本详情 + 前 50 行预览 |
| `GET /api/projects/{projectId}/data-files/{id}/versions/{versionNo}/download` | 下载（`Content-Disposition` 用 original_filename） |
| `DELETE /api/projects/{projectId}/data-files/{id}` | 硬删主表 + 全部版本记录与文件 |

- 上传流式处理（`DigestingInputStream` 摘要 + 首行解析 + 计行），不整文件进内存；超限返回 400 `DATAFILE_TOO_LARGE`。
- **场景侧无新端点**：绑定随现有场景档案更新提交（`applyScenarioProfile` 扩展 `dataFileBindings` 参数，同 threadGroupConfigs 口径）。

### 3.4 计划文档侧绑定（前端）

`ScenarioDialog.vue`（场景编辑弹窗，已含线程组档位区）新增「CSV 数据文件」区：

- 绑定脚本后按脚本解析结果列出 CSV_DATA 步骤（stepName + fileName + variableNames 摘要）；
- 每行下拉选择项目数据文件（选项显示 name · vN · 行数 · 大小，hover 展示表头列名，辅助与脚本 variableNames 核对）；
- 未绑定行 = 维持现状（裸 fileName + 兜底匹配）；
- 提交走现有场景保存；换绑脚本版本后服务端 normalize 剔除失效 stepId（同线程组档位行为）。

### 3.5 脚本编辑器 CSV 步骤升级（前端）

- `StepDetail.vue` CSV 分支补齐属性输入：delimiter / fileEncoding / ignoreFirstLine / recycle / stopThread / shareMode（select：all threads / current thread / thread group）；
- fileName 保持手填（相对名），表单加说明文案「数据文件在 计划 → 场景 中绑定分发」；
- 不加数据文件下拉（绑定在场景侧，C2）。

数据文件管理页放脚本编辑器工作区 `ScriptWorkspace.vue` 左栏，与脚本版本时间线同区以标签页切换「脚本 / 数据文件」（不新增顶级导航）：列表 + 上传（a-upload 自定义请求，仿 `MethodEvidenceShots.vue`）+ 版本时间线 + 预览 + 下载 + 删除（二次确认）。

### 3.6 执行装配与分发链路

```
DistributedJmeterExecutionRunner.loadPreparation
  └─ ExecutionScriptAssembler.prepare（扩展点，保持唯一装配 seam）
      ① 现有：normalize → 线程组预设补丁 → BackendListener 注入
      ② 新增（注入前）：CSV 数据文件装配
         a. parse JMX → CSV_DATA 步骤（含 fileName、stepId）
         b. 场景 dataFileBindings 按 stepId 匹配 → 取该 dataFile 最新版本
         c. 未绑定步骤：执行目录无此文件 → 按 original_filename 唯一匹配项目数据文件兜底
         d. 校验：绑定引用存在性（缺失/被删抛错，指明步骤名）；目标 fileName 集合无重复
         e. 复制 stored_path → 执行目录/{fileName}；写 data-files.json manifest
  └─ JmeterDependencyCollector.collect（不动，自然收集执行目录内 CSV）
  └─ payload.dependencies（不动）
  └─ remote-runner main.py
      ① launch_controller（不动：已全量 SFTP）
      ② start_worker：改为全量 dependencies SFTP（C5）；容器内 cp /test/*.jar → lib/ext 保留
```

- runner 向 prepare 传场景绑定（与线程组预设同路，`PersistentTaskScenarioRecord` 直接可读）；
- **路径解析假设（实施验证点）**：JMX 内 CSV filename 为相对路径，JMeter 按 CWD 解析；容器未显式 `-w`，依赖 justb4/jmeter 镜像 WORKDIR=/test（与现有 `cp /test/*.jar` 同假设）。实施计划含验证任务：`docker inspect` 确认，必要时 controller/worker `docker run` 补 `-w /test`。

### 3.7 校验与错误处理

| 场景 | 行为 |
|------|------|
| 上传超 100MB / 非 csv 文本（扩展名 + 首行可解析性） | 400 拒绝 |
| 绑定的 stepId 不在当前脚本 | 服务端 normalize 剔除（保存时即清，同线程组档位） |
| 绑定的 dataFileId 已被删除 | 装配抛 `ExecutionValidationException`：「步骤 [xx] 绑定的数据文件 [yy] 不存在」 |
| 同脚本 CSV fileName 相同 | 装配显式报错（步骤名 + 冲突名） |
| 裸 filename 兜底歧义（多文件同名） | 跳过补发（维持现状）；manifest 不记 |

### 3.8 存量兼容

- 存量 JMX 的 CSV 步骤解析、渲染、执行行为不变（绑定与属性全可选）；
- `data_file_bindings_json` 空值 = 无绑定，走现状路径；
- manifest 只在装配成功后存在，无向后读取方。

---

## 4. P1-7 JSR223 前后置处理器

### 4.1 决策记录

| # | 决策 | 内容 |
|---|------|------|
| J1 | 挂载层级 | **请求级 + 线程组级**（现有 `MAX_SCRIPT_STEP_LEVEL=2` 框架内）。TestPlan 级不做（YAGNI） |
| J2 | 模型 | 复用已预留的步骤类型；config = `scriptLanguage`（默认 `groovy`）/ `script` / `parameters` / `cacheKey`（默认 `true`，与 BackendListenerInjector 惯例一致） |
| J3 | 语言 | **Groovy only**（新建时锁定）；导入外部 JMX 若为 beanshell/java 等，原值保留展示（不丢），可切 groovy |
| J4 | JMX 渲染 | renderer 手拼 + 现有 `xml()` 五实体转义（与全部现有元素一致；JMeter 官方保存格式即转义文本，禁 CDATA 硬拼）；标签 `JSR223PreProcessor` / `JSR223PostProcessor`（`guiclass="TestBeanGUI"`、`testclass` 同标签）；parser / `JmeterScriptDom.stepType` / patcher 对称支持 |
| J5 | 片段库 | 后端 `resources/jsr223-snippets.json`（仿 `jmeter-functions/functions.json`）+ `GET /api/jsr223-snippets` 只读下发；内置：HMAC-SHA256 签名、AES-256 加/解密、MD5/Base64、RSA 签名、UUID/时间戳/随机数。片段元数据 = key/name/category/description/params/code；**密钥位一律 `${__P(...)}` 占位**，说明写明对应场景属性名 |
| J6 | 编辑器高亮（**用户确认新增依赖**） | 引入 `@codemirror/lang-groovy`，`CodeEditor.vue` 增加 groovy 语言支持，精确高亮 |
| J7 | `${__P}` 透传修复 | **纳入本期**：`config.jmeterProperties` 进 payload（`Map<String,String>`）；remote-runner `launch_controller` 命令行逐条注入 `shell_quote(f"-J{k}={v}")` + `shell_quote(f"-G{k}={v}")`——`-J` 供 controller 本地，`-G` 经 RMI 同步全部 worker。D17 密钥参数化的前置基础 |
| J8 | 明文密钥防护（**用户确认：扫描+警告**） | 保存脚本时后端对 JSR223 步骤 `script` 轻量扫描（常见密钥名 secret/password/token/key/salt/appkey 赋值 + ≥16 位 hex/base64 字面量），命中在保存响应体带 `warnings[]`，**不阻断**；前端黄条提示「疑似明文密钥，建议参数化引用」 |
| J9 | D17 边界解读 | 红线 = **密钥不进脚本内容（JMX 明文）**；平台不做 vault。密钥值载体由用户选：场景属性（`jmeterProperties`，明文存库、用户自担）、CSV 数据文件列（P1-5）、执行时环境。本期交付红线检查（J8）+ 参数化引用能力（J7） |
| J10 | 单机语义 | 不恢复遗留 `ExecutionMode.LOCAL` / `JmeterCommandExecutor`（无调用方）。「单机生效」= 导出 JMX 在任意本地 JMeter 打开元素完整可用（密钥属性用 `-J` 传） |
| J11 | USER_PARAMS 空壳 | **不修**（`appendUserParams` 渲染空元素是既有缺陷，与本期无关；且密钥进 USER_PARAMS = 明文进 JMX，违反 D17 红线）。列 known issue |

### 4.2 config ↔ JMX 映射

```json
{ "scriptLanguage": "groovy", "script": "def sign = ...", "parameters": "env=prod", "cacheKey": true }
```

```xml
<JSR223PreProcessor guiclass="TestBeanGUI" testclass="JSR223PreProcessor" testname="签名处理器" enabled="true">
  <stringProp name="cacheKey">true</stringProp>
  <stringProp name="scriptLanguage">groovy</stringProp>
  <stringProp name="parameters">env=prod</stringProp>
  <stringProp name="filename"></stringProp>
  <stringProp name="script">def sign = ...（xml() 转义文本）</stringProp>
</JSR223PreProcessor>
<hashTree/>
```

- 属性命名与 `JmeterBackendListenerInjector`（:114-126）及 JSR223TestBean 约定一致；`filename` 恒空（内联脚本，`JmeterDependencyCollector` 对空值自然跳过）；
- PostProcessor 仅标签不同；JMX 语义由挂载位置决定（请求子元素 = 该采样前后；线程组子元素 = 组内每个采样器前后）；
- `cacheKey=true`：JMeter 按脚本文本缓存编译，内容变化自动重编译。

### 4.3 后端改动

| 位置 | 改动 |
|------|------|
| `JmeterScriptRenderer` | switch 加两 case + `appendJsr223Processor(tag, step)`（script/parameters 经 `xml()` 转义） |
| `JmeterScriptParser` | `parseJsr223`：五属性读入 config（`cacheKey` 缺省 true） |
| `JmeterScriptDom.stepType` | 标签 → 枚举（stepId 前缀 `jsr223-pre-` / `jsr223-post-`） |
| `JmeterScriptPatcher` | 无需改（走 `renderStepFragment` 通用路径）；未识别元素原样保留机制继续保护手工 JMX |
| `jsr223-snippets.json` + Controller | 仿 `JmeterFunctionRegistry`：classpath 读取、`GET /api/jsr223-snippets` |
| 密钥扫描 `Jsr223SecretScanner` | 保存链路（`ScriptService.saveScriptContent`）挂接，响应体加 `warnings`；纯正则、只扫 JSR223 步骤 |
| `DistributedJmeterExecutionRunner.payload()` | 增加 `jmeterProperties` = `config.jmeterProperties()` |
| remote-runner `main.py` | `launch_controller` 注入 `-J{k}={v}` + `-G{k}={v}`（逐条 `shell_quote`） |

### 4.4 前端改动

| 位置 | 改动 |
|------|------|
| `constants/index.ts` + `types/index.ts` + `StepTypeIcon.vue` | 类型/元数据/图标补两个 JSR223 类型；可挂载：THREAD_GROUP 与 HTTP_REQUEST 子类型集合 |
| `StepCreateDialog.vue` | 两张类型卡片（前置/后置处理器） |
| `StepDetail.vue` | JSR223 表单：名称、语言（只读 groovy；导入他语言显示原值）、参数、脚本（`CodeEditor` groovy 模式）、「插入片段」按钮 |
| 片段面板 | 仿 `VariablePanel`：按 category 分组 + 说明 + 点击插入光标处；数据来自 `/api/jsr223-snippets` |
| `${` 联想 | `HttpRequestConfig` 的联想机制复用到脚本编辑框（可选增强，实施时评估封装成本，贵则砍） |
| 保存提示 | 保存响应含 `warnings` 时黄条展示 |
| `jmeter-xml-import.ts` / `script-steps.ts` | 导入识别两标签（属性往返）；默认 config |

### 4.5 执行链路生效性

- 内联 Groovy 随 JMX 分发：controller 从 `/test/*.jmx` 启动、JMX 经 RMI 全量下发 worker——**双端天然生效**；
- `${__P(signKey)}` 取值：controller `-JsignKey=...`、worker 由 `-GsignKey=...` RMI 同步（J7）；
- 片段签名示例以 `vars.put("sign", ...)` + HTTP 请求参数 `${sign}` 闭环。

### 4.6 存量兼容

- 存量 JMX 手工 JSR223 元素：现在被 patcher「未识别原样保留」；识别后转平台可管理步骤（解析/展示/编辑/替换），属性全量往返、无损升级；
- 未建模属性（自定义 stringProp）：解析丢弃、渲染不输出（与现有元素往返策略一致；XML 源码模式可手改完整 XML）；
- `jmeterProperties` 透传对存量场景无副作用（未配置 = 空 map = 无新增参数）。

---

## 5. 测试口径

**P1-5**：
- 后端：上传（新建/追加版本/超限/无表头）、绑定 normalize（换脚本剔失效 stepId）、装配（绑定解析、裸名兜底唯一匹配、缺失/冲突报错）、manifest、renderer/parser 新属性往返；
- remote-runner：worker 收到非 jar 依赖、targetPath 平铺；
- 端到端：上传 CSV → 计划场景关联脚本后下拉绑定 → 多节点执行 → controller 与全部 worker 的 `{remoteWorkDir}/{runId}/` 均有该文件 → 执行成功消费数据（variableNames 出现在请求参数）。

**P1-7**：
- 后端：renderer/parser 往返（含 `<`/`&`/引号/换行的 Groovy）、DOM stepType 识别、patcher 按 id 替换、密钥扫描命中/误报样例、payload 含 jmeterProperties；
- remote-runner：controller 命令行含 `-J`/`-G`（特殊字符 quote）；
- 端到端：编辑器给请求加「HMAC-SHA256 签名 PreProcessor（密钥 `${__P(signKey)}`）」→ 场景属性配 signKey → 分布式执行，响应断言/`__log` 验证签名生效；导出 JMX 在本地 JMeter GUI 打开元素完整。
