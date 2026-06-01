# 函数库模块

## 1. 模块定位

函数库用于管理 JMeter 脚本中可复用的 Groovy 函数和工具片段，提升复杂脚本维护效率。该模块属于远期扩展，不进入 MVP。

## 2. 职责边界

| 负责 | 不负责 |
|------|--------|
| Groovy 函数管理 | JMeter 核心执行引擎 |
| 函数分类、版本和说明 | 业务脚本自动重写 |
| 在线调试和测试输入 | 生产环境命令执行 |
| 函数引用记录 | AI 自动生成函数 |

## 3. 功能范围

1. Groovy 自定义函数管理、分类、搜索。
2. 函数版本记录和变更说明。
3. 在线调试，输入参数后返回输出、日志和异常。
4. 项目私有函数和系统公共函数。
5. 测试任务可选择函数包版本作为执行依赖。

## 4. 关键实体

| 实体 | 关键字段 | 说明 |
|------|----------|------|
| `function_entry` | `id`, `project_id`, `name`, `category`, `scope`, `status` | 函数主记录 |
| `function_version` | `id`, `function_id`, `version_no`, `source_code`, `remark`, `created_by` | 函数版本 |
| `function_debug_record` | `id`, `version_id`, `input_json`, `output_text`, `error_message`, `duration_ms` | 调试记录 |

## 5. 接口草案

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/projects/{projectId}/functions` | 函数列表 |
| `POST` | `/api/projects/{projectId}/functions` | 创建函数 |
| `POST` | `/api/functions/{functionId}/versions` | 新增函数版本 |
| `POST` | `/api/function-versions/{versionId}/debug` | 调试函数 |
| `GET` | `/api/function-versions/{versionId}/download` | 下载函数源码 |

## 6. 详细设计调整点

1. Groovy 执行必须设置沙箱、超时、内存限制和可访问类白名单。
2. 调试环境与正式压测执行环境隔离，避免调试代码影响执行任务。
3. 函数版本不可变，任务引用函数版本后不受后续编辑影响。
4. 前端编辑器后续使用 Monaco Editor，但不要引入到 MVP 包体。
5. 函数库和脚本管理只通过版本引用关联，不直接修改 JMX 文件。
