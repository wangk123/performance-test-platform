## Why

业务压测前置造数若依赖完整业务 API 编排，常因链路过长、限流或非目标瓶颈而阻塞；手写字段规则成本又过高。需要在测试环境支持「点一次业务 + 录库变更 → 确认模板 → 直连写库批量克隆」，以较低建模成本覆盖 INSERT / UPDATE 混合场景。

## What Changes

- 新增项目级测环境数据源（MySQL JDBC）与库表过滤策略（精确名 + 表达式，include/exclude 混合）。
- 新增录制会话：过滤后表快照 Diff、多样本采集；采集实现可插拔（V1 快照，预留 binlog）。
- 新增推断引擎：基于元数据 + 多样本信号推断字段角色与表间关联，置信为 HIGH/MEDIUM/LOW + 可展示依据。
- 新增造数模板确认硬门禁：未确认不可克隆；确认后版本只读。
- 新增克隆任务：按已确认模板批量受控写库（INSERT/UPDATE）；单批事务、失败策略与审计。
- 项目「造数工厂」页从占位改为可用流程；替换/收敛现有 Mock API。
- **不做（本变更）**：DELETE 克隆、binlog 采集实现、参数文件导出、业务 API 灌数编排、细粒度权限、生产库写入。

## Capabilities

### New Capabilities

- `seed-datasource`: 项目测环境数据源与库表过滤策略
- `seed-capture`: 录制会话、快照 Diff 采集、多样本
- `seed-template`: 字段角色推断、模板确认门禁与版本
- `seed-clone`: 批量写库克隆任务与执行结果

### Modified Capabilities

- （无）现有 `openspec/specs/` 无造数相关 capability；文档草案 `docs/modules/07-test-data-factory.md` 不作为正式 spec。

## Impact

- 后端：新模块（数据源/录制/模板/克隆）、持久化表、异步或长任务执行；依赖 JDBC 与 MySQL 元数据读取。
- 前端：项目「造数工厂」Tab 实装（数据源、过滤、录制、确认、克隆任务）。
- 函数库：确认页生成器绑定复用既有 `randomMobile` 等元数据/实现，不新增 JMeter 运行时耦合。
- 脚本/执行/辅助脚本：本变更不耦合；造数完成后由用户自行发起压测。
