# 脚本管理模块

## 1. 模块定位

脚本管理负责 JMeter JMX 文件的上传、存储、版本记录、参数识别和默认执行参数维护。它只管理可执行资产，不直接启动压测任务。

## 2. 职责边界

| 负责 | 不负责 |
|------|--------|
| JMX 文件上传和校验 | JMeter 进程启动 |
| 脚本版本追加和追溯 | 执行队列调度 |
| 默认执行参数维护 | 报告指标聚合 |
| 脚本文件存储路径管理 | Git 仓库同步 |

## 3. 需求规格

### REQ-SCRIPT-001 上传 JMX 脚本

**用户故事**：作为性能测试工程师，我希望上传 JMeter JMX 文件，作为后续执行任务的脚本来源。

**功能说明**：

1. 支持在项目下上传 `.jmx` 文件。
2. 上传后生成脚本记录和脚本版本。
3. 记录文件名、文件大小、文件哈希、上传人、上传时间、版本号和存储路径。
4. 同一脚本再次上传时生成新版本，不覆盖历史版本。

**输入/输出**：

| 类型 | 内容 |
|------|------|
| 输入 | 项目 ID、JMX 文件、脚本名称、备注 |
| 输出 | 脚本详情、版本号、上传结果 |

**异常场景**：

1. 文件扩展名不是 `.jmx` 时拒绝上传。
2. 文件超过系统限制时拒绝上传。
3. JMX 解析失败时保存失败原因，脚本版本状态为解析失败。

**验收标准**：

1. 用户可以上传合法 JMX 文件，并在脚本列表看到脚本和版本号。
2. 同一脚本上传第二次后，版本号递增，旧版本仍可查看。
3. 非 JMX 文件无法上传，并提示文件类型不支持。

### REQ-SCRIPT-002 配置脚本参数

**用户故事**：作为性能测试工程师，我希望在执行前配置线程数、循环次数等参数，减少直接修改 JMX 文件的频率。

**功能说明**：

1. 支持为脚本版本维护默认执行参数。
2. MVP 参数包括线程数、循环次数、持续时间、Ramp-Up、目标环境、JMeter 属性扩展项。
3. 执行任务可复制脚本默认参数，并允许本次执行覆盖。

**输入/输出**：

| 类型 | 内容 |
|------|------|
| 输入 | 脚本版本 ID、参数键值、参数说明 |
| 输出 | 参数列表、参数校验结果 |

**异常场景**：

1. 线程数、循环次数、持续时间不能为负数。
2. 参数键重复时拒绝保存。
3. 已执行任务的历史配置不随脚本默认参数变化而改变。

**验收标准**：

1. 用户可以为脚本版本保存默认执行参数。
2. 新建任务时自动带出默认参数。
3. 修改脚本默认参数不会改变已存在执行记录中的执行配置。

## 4. 关键实体与版本状态机

| 实体 | 关键字段 | 说明 |
|------|----------|------|
| `scripts` | `id`, `project_id`, `name`, `latest_version_no`(水位，只增不减), `created_by`, `created_at` | 脚本主表 |
| `script_versions` | `id`, `script_id`, `version_no`(DRAFT 固定 0), `status`, `remark`, `original_filename`, `stored_path`, `uploaded_by`, `uploaded_at`, `updated_at` | 脚本版本（每脚本至多一条 DRAFT，`(script_id, version_no)` 唯一） |
| `script_param` | `id`, `script_version_id`, `param_key`, `param_value`, `value_type`, `description`, `updated_at` | 默认参数 |

版本状态机（DRAFT/PUBLISHED 两态单向，详见 spec `2026-09-17-script-version-publish-design.md`）：

```text
DRAFT --发布(手动版本号 + 必填变更说明)--> PUBLISHED（不可变快照）
DRAFT --删除/丢弃--> 物理删除（随时）
PUBLISHED --删除(当前无场景引用)--> 物理删除；被引用则拒绝并报出场景名
```

要点：

1. 版本号发布时手动指定，仅限大于 `latest_version_no`；水位只增不减，杜绝同号复用。
2. 场景绑定钉死具体已发布版本（`script_version_id`），绑定/执行入口校验 `status = PUBLISHED`；快捷执行遇 DRAFT 自动发布为快照。
3. 删除脚本 = 级联删全部版本与文件，任一 PUBLISHED 版本被场景引用则整体拒绝。
4. 历史执行追溯靠 `storage/executions/` 执行目录中的 JMX 复制件自包含，不依赖源版本存活。

## 5. 接口草案

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/projects/{projectId}/scripts/assets` | 脚本维度聚合列表（最新发布 + 草稿标记 + 场景使用统计） |
| `GET` | `/api/projects/{projectId}/scripts/{scriptId}/versions` | 版本历史（含每版本引用场景名） |
| `POST` | `/api/projects/{projectId}/scripts` | 上传 JMX（建脚本 + PUBLISHED v1，同名追加新发布版本）或 JSON 新建空白脚本（DRAFT 脚手架） |
| `PUT` | `/api/projects/{projectId}/scripts/{scriptId}/draft` | 保存草稿（惰性创建，基准 = 最新已发布或脚手架） |
| `POST` | `/api/projects/{projectId}/scripts/{scriptId}/publish` | 发布（版本号 + 变更说明必填） |
| `POST` | `/api/projects/{projectId}/scripts/{scriptId}/fork-draft` | 以指定已发布版本内容重建草稿（回滚便利键） |
| `DELETE` | `/api/projects/{projectId}/scripts/{scriptId}/versions/{versionId}` | 删版本（引用守卫） |
| `DELETE` | `/api/projects/{projectId}/scripts/{scriptId}` | 删脚本（级联守卫） |
| `GET` | `/api/projects/{projectId}/scripts/{versionId}` / `/{versionId}/definition` | 版本内容/结构定义（编辑器现拉） |

## 6. 详细设计调整点

1. 文件存储按 `storage/scripts/{projectId}/{scriptId}/v{versionNo}-{safeFileName}` 组织，避免不同项目文件冲突。
2. 文件名必须做安全清洗，不允许路径穿越字符影响存储路径。
3. 文件哈希用于追溯和重复上传提示，不作为拒绝上传的唯一依据，因为同一文件可能需要作为新版本留痕。
4. JMX 参数识别首版可以只识别线程组常见字段，无法识别时仍允许手工维护默认参数。
5. 脚本默认参数只作为新建任务模板，提交任务时必须复制成执行配置快照。
