## ADDED Requirements

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
- **WHEN** POST /api/task-plans/{planId}/scenarios with scriptVersionId and execution parameters
- **THEN** a new scenario SHALL be created under the plan

### Requirement: Execution API by execution id
The system SHALL expose execution detail, logs, result, samples, and monitoring under /api/executions/{executionId}.

#### Scenario: Query historical execution
- **WHEN** GET /api/executions/{executionId}/result for a completed execution
- **THEN** the system SHALL return metrics for that specific execution, not the latest one
