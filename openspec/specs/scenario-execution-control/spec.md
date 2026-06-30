# scenario-execution-control

## Purpose

执行生命周期控制，包括主动停止运行中的执行和删除历史执行记录。

## Requirements

### Requirement: Stop running execution
The system SHALL allow stopping a RUNNING or QUEUED execution via POST /api/executions/{executionId}/stop.

#### Scenario: Stop distributed run
- **WHEN** a user stops a running execution
- **THEN** the runner SHALL invoke remote stop-run and the execution status SHALL become INTERRUPTED

#### Scenario: Reject stop on finished execution
- **WHEN** POST /api/executions/{executionId}/stop on a SUCCESS execution
- **THEN** the system SHALL return 400

### Requirement: Delete execution record
The system SHALL allow deleting a non-running execution via DELETE /api/executions/{executionId}.

#### Scenario: Delete completed record
- **WHEN** DELETE /api/executions/{executionId} for a SUCCESS execution
- **THEN** the record SHALL be removed and monitor bindings deleted

#### Scenario: Reject delete running
- **WHEN** DELETE /api/executions/{executionId} for a RUNNING execution
- **THEN** the system SHALL return 400
