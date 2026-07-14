## MODIFIED Requirements

### Requirement: Scenario CRUD API
The system SHALL expose scenario endpoints under /api/task-plans/{planId}/scenarios and /api/scenarios/{scenarioId}.

#### Scenario: Add scenario to plan
- **WHEN** POST /api/task-plans/{planId}/scenarios with scriptVersionId, name, and optional execution node/monitor configuration
- **THEN** a new scenario SHALL be created under the plan
- **AND** the request SHALL NOT contain threads, rampUp, duration, or loops parameters

#### Scenario: Update scenario
- **WHEN** PUT /api/scenarios/{scenarioId} with updated name, scriptVersionId, or execution node/monitor configuration
- **THEN** the scenario SHALL be updated
- **AND** the request SHALL NOT contain threads, rampUp, duration, or loops parameters

## REMOVED Requirements

### Requirement: Thread parameters in scenario API
**Reason**: 线程配置以脚本中的 ThreadGroup 为唯一数据源，场景 API 不再接受线程参数
**Migration**: 客户端停止发送 threads/rampUp/duration/loops 字段；服务端忽略旧请求中的这些字段
