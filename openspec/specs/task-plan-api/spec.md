# task-plan-api

## Purpose

任务计划、场景、执行的 REST API 契约，提供计划 CRUD、场景 CRUD 和按 executionId 查询执行详情的端点。

## Requirements

### Requirement: Task plan CRUD API
The system SHALL expose REST endpoints for task plan management under /api/projects/{projectId}/task-plans and /api/task-plans/{planId}.

#### Scenario: Create plan
- **WHEN** POST /api/projects/{projectId}/task-plans with name and default execution config
- **THEN** the response SHALL return 201 with the created plan id

#### Scenario: List plans
- **WHEN** GET /api/projects/{projectId}/task-plans
- **THEN** the response SHALL return all plans for the project ordered by id desc

### Requirement: Scenario CRUD API
The system SHALL expose scenario endpoints under /api/task-plans/{planId}/scenarios and /api/scenarios/{scenarioId}.

#### Scenario: Add scenario to plan
- **WHEN** POST /api/task-plans/{planId}/scenarios with scriptVersionId, name, optional threadGroupConfigs, and optional execution node/monitor configuration
- **THEN** a new scenario SHALL be created under the plan
- **AND** the request SHALL NOT contain legacy threads, rampUp, duration, or loops scalar fields on the scenario root

#### Scenario: Update scenario
- **WHEN** PUT /api/scenarios/{scenarioId} with updated name, scriptVersionId, threadGroupConfigs, or execution node/monitor configuration
- **THEN** the scenario SHALL be updated

#### Scenario: Scenario response includes configs with latest summary
- **WHEN** GET /api/task-plans/{planId}/scenarios or GET /api/scenarios/{scenarioId}
- **THEN** each threadGroupConfig SHALL include optional latestSummary with samples, throughput, avgRt, and errorRate from the most recent completed execution using that preset

#### Scenario: Batch delete executions
- **WHEN** DELETE /api/executions/batch with a list of execution IDs
- **THEN** all specified executions SHALL be deleted
- **AND** the response SHALL return 204 No Content

### Requirement: Execution trigger API
The system SHALL accept an optional threadGroupConfigId when triggering scenario execution.

#### Scenario: Trigger with preset
- **WHEN** POST /api/scenarios/{scenarioId}/executions with threadGroupConfigId
- **THEN** the execution SHALL apply that preset to the target Thread Group step

#### Scenario: Trigger with script default
- **WHEN** POST /api/scenarios/{scenarioId}/executions without threadGroupConfigId
- **THEN** the execution SHALL use script native Thread Group configuration

### Requirement: Execution API by execution id
The system SHALL expose execution detail, logs, result, samples, and monitoring under /api/executions/{executionId}.

#### Scenario: Query historical execution
- **WHEN** GET /api/executions/{executionId}/result for a completed execution
- **THEN** the system SHALL return metrics for that specific execution, not the latest one
