# 造数工厂模块

## 1. 模块定位

造数工厂用于沉淀性能测试数据准备能力，帮助用户按模板生成 CSV、JSON、SQL 等格式测试数据。该模块属于远期扩展，不进入 MVP。

## 2. 职责边界

| 负责 | 不负责 |
|------|--------|
| 数据模板管理 | 压测执行调度 |
| 字段规则配置 | JMX 文件解析 |
| 数据预览和导出 | 生产数据库直连写入 |
| 生成记录追溯 | AI 数据生成 |

## 3. 功能范围

1. 数据模板、字段定义、生成规则管理。
2. 支持内置规则：随机字符串、手机号、邮箱、身份证样例、日期范围、枚举值、自增序列。
3. 支持 CSV、JSON、SQL 格式导出。
4. 支持模板归属项目或系统公共库。
5. 记录生成批次、生成参数、生成文件和操作人。

## 4. 关键实体

| 实体 | 关键字段 | 说明 |
|------|----------|------|
| `data_template` | `id`, `project_id`, `name`, `scope`, `description`, `created_by` | 数据模板 |
| `data_field_rule` | `id`, `template_id`, `field_name`, `rule_type`, `rule_config`, `sort_order` | 字段规则 |
| `data_generation_job` | `id`, `template_id`, `row_count`, `format`, `status`, `file_path`, `created_by` | 生成任务 |

## 5. 接口草案

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/projects/{projectId}/data-templates` | 模板列表 |
| `POST` | `/api/projects/{projectId}/data-templates` | 创建模板 |
| `PUT` | `/api/data-templates/{templateId}/fields` | 保存字段规则 |
| `POST` | `/api/data-templates/{templateId}/preview` | 数据预览 |
| `POST` | `/api/data-templates/{templateId}/generate` | 生成数据文件 |
| `GET` | `/api/data-generation-jobs/{jobId}/download` | 下载生成文件 |

## 6. 详细设计调整点

1. 造数结果默认生成文件下载，不直接写入被测数据库，降低误操作风险。
2. 规则配置使用 JSON 存储，字段规则引擎通过接口扩展。
3. 大批量生成必须异步执行，并限制单次最大行数和文件大小。
4. 模板可以项目私有或系统公共，权限仍复用项目成员和管理员模型。
5. 后续可把生成数据文件作为测试任务附件，但不应耦合到执行模块的核心流程。
