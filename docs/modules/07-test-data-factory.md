# 造数工厂模块

## 1. 模块定位

造数工厂用于测试环境前置批量造数：用户配置测环境数据源与库表过滤，录制业务操作引起的库变更，经人工确认模板后，直连写库批量克隆。V1 产出仅为写库，不导出参数文件，不编排业务 API 灌数。

## 2. 职责边界

| 负责 | 不负责 |
|------|--------|
| 测环境数据源与过滤策略 | 压测执行调度 |
| 快照录制与多样本 Diff | 生产库写入 |
| 字段角色推断与模板确认 | 参数文件导出（后期） |
| 批量 INSERT/UPDATE 克隆 | 业务 API 灌数编排（后期） |
| | DELETE 克隆、binlog 采集（后期） |
| | 细粒度权限（后期统一） |

## 3. 主流程

```
数据源 → 过滤(include/exclude) → 快照录制×N → 推断 →【确认】→ 克隆写库
```

## 4. 关键实体

| 实体 | 说明 |
|------|------|
| `seed_datasource` | 项目测环境 MySQL 连接（密码加密存储） |
| `seed_capture_strategy` | 可复用采集配置与版本 |
| `seed_capture_sample` | 一次异步快照执行及其分片 |
| `seed_capture_analysis` | 多样本相邻 Diff 与模板草稿来源 |
| `seed_template` | 草稿/已确认模板版本 |
| `seed_clone_job` | 克隆任务与结果审计 |

## 5. 主要接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET/POST` | `/api/projects/{id}/seed/datasources` | 数据源列表/创建 |
| `POST` | `/api/projects/{id}/seed/datasources/{dsId}/test` | 测连 |
| `GET/POST/PUT/DELETE` | `/api/projects/{id}/seed/capture-strategies` | 管理采集策略 |
| `POST` | `/api/projects/{id}/seed/capture-strategies/{strategyId}/execute` | 执行异步样本 |
| `GET` | `/api/projects/{id}/seed/capture-strategies/{strategyId}/samples` | 查询样本历史 |
| `POST` | `/api/projects/{id}/seed/capture-analyses` | 创建多样本分析 |
| `GET/PUT` | `/api/projects/{id}/seed/templates/{tid}` | 模板详情/保存草稿 |
| `POST` | `/api/projects/{id}/seed/templates/{tid}/confirm` | 确认生效 |
| `POST` | `/api/projects/{id}/seed/clone-jobs` | 创建并执行克隆 |

## 6. 设计要点

1. 采集实现可插拔；V1 仅 SNAPSHOT，BINLOG 返回不支持。
2. 过滤必须至少一条 include；exclude 优先；支持精确名与表达式（通配 / `regex:`）。
3. 确认是硬门禁；LOW 置信须采纳；UNIQUE_REGEN/FORMATTED_RAND 须绑定生成器。
4. 克隆按批事务；失败策略 CONTINUE（默认）或 STOP；有最大 N 限制。
