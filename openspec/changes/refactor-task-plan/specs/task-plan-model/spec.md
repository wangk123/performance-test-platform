## ADDED Requirements

### Requirement: Three-layer task model
The system SHALL model performance testing as TaskPlan → TaskScenario → ScenarioExecution.

#### Scenario: Plan contains multiple scenarios
- **WHEN** a user creates a task plan with two scenarios bound to different script versions
- **THEN** both scenarios SHALL be listed under the plan and each SHALL reference exactly one script version

#### Scenario: Scenario produces multiple executions
- **WHEN** a user triggers execution on the same scenario three times
- **THEN** three ScenarioExecution records SHALL exist and all SHALL remain queryable

### Requirement: Configuration inheritance
The system SHALL merge plan-level defaults with scenario-level overrides when creating an execution snapshot.

#### Scenario: Scenario inherits plan nodes
- **WHEN** a plan defines controllerNodeId=1 and a scenario has null controllerNodeId
- **THEN** the execution snapshot SHALL use controllerNodeId=1

#### Scenario: Scenario overrides plan monitors
- **WHEN** a plan defines monitorTargetIds=[1,2] and a scenario defines monitorTargetIds=[3]
- **THEN** the execution snapshot SHALL use monitorTargetIds=[3]

### Requirement: No plan-wide execution
The system SHALL NOT provide an API to execute an entire plan at once.

#### Scenario: Only scenario execution endpoint
- **WHEN** a client wants to run a test
- **THEN** the client MUST call POST /api/scenarios/{scenarioId}/executions
