# 分布式执行模块

## 1. 模块定位

分布式执行用于突破单机执行能力限制，支持多执行节点注册、容量调度、任务分片和结果聚合。该模块必须等单机执行稳定并出现明确容量瓶颈后再启动。

## 2. 职责边界

| 负责 | 不负责 |
|------|--------|
| 执行节点注册和心跳 | 项目业务建模 |
| 节点容量、标签和状态管理 | 基础报告指标定义 |
| 分布式任务调度和结果回收 | 造数规则管理 |
| 节点失败处理和重试 | AI 分析 |
| 平台自身监控和告警 | 企业级 CMDB 替代 |

## 3. 功能范围

1. 执行节点注册、心跳、标签和容量管理。
2. 分布式任务调度、任务分片、结果聚合。
3. Redis、RabbitMQ 或 Kafka 等中间件接入。
4. 多节点 JMeter 执行和结果回收。
5. 平台运行监控、告警、限流、配额和清理策略。

## 4. 关键实体

| 实体 | 关键字段 | 说明 |
|------|----------|------|
| `execution_node` | `id`, `name`, `host`, `status`, `labels`, `capacity`, `last_heartbeat_at` | 执行节点 |
| `distributed_task_plan` | `id`, `task_id`, `strategy`, `shard_count`, `status` | 分布式执行计划 |
| `task_shard` | `id`, `plan_id`, `node_id`, `shard_no`, `status`, `result_path` | 任务分片 |
| `node_heartbeat` | `id`, `node_id`, `cpu_usage`, `memory_usage`, `active_tasks`, `created_at` | 节点心跳 |

## 5. 接口草案

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/execution-nodes/register` | 节点注册 |
| `POST` | `/api/execution-nodes/{nodeId}/heartbeat` | 节点心跳 |
| `GET` | `/api/execution-nodes` | 节点列表 |
| `POST` | `/api/tasks/{taskId}/distributed-plan` | 创建分布式执行计划 |
| `GET` | `/api/distributed-plans/{planId}/shards` | 查看分片状态 |
| `POST` | `/api/task-shards/{shardId}/result` | 上报分片结果 |

## 6. 详细设计调整点

1. `TaskScheduler` 从单机实现切换为队列实现时，外部任务接口应尽量保持不变。
2. `TestExecutor` 支持本地执行和远程节点执行两个实现，任务模块只依赖接口。
3. 结果聚合必须处理节点失败、部分成功和重复上报。
4. 文件存储需要从本地文件系统升级为 MinIO 或 OSS，否则多节点结果回收复杂度过高。
5. 平台自身指标接入 Prometheus 和 Grafana，但这属于分布式治理阶段，不反向影响 MVP。
