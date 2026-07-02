# task-plan-model

## Purpose

任务计划的数据模型，定义 TaskPlan → TaskScenario → ScenarioExecution 三层结构，替代旧的单层 Task 模型，支持多场景压测计划与执行历史全量保留。

## Requirements

### Requirement: Three-layer task model
The system SHALL model performance testing as TaskPlan → TaskScenario → ScenarioExecution.

#### Scenario: Plan contains multiple scenarios
- **WHEN** a user creates a task plan with two scenarios bound to different script versions
- **THEN** both scenarios SHALL be listed under the plan and each SHALL reference exactly one script version

#### Scenario: Scenario produces multiple executions
- **WHEN** a user triggers execution on the same scenario three times
- **THEN** three ScenarioExecution records SHALL exist and all SHALL remain queryable

### Requirement: Scenario thread group configs
The system SHALL allow each TaskScenario to store multiple thread group configuration presets without user-defined names.

#### Scenario: Scenario stores thread group presets
- **WHEN** a scenario is created or updated with threadGroupConfigs
- **THEN** each preset SHALL include stepId, stepName, threads, rampUp, duration, and sortOrder
- **AND** presets SHALL NOT include a user-defined name field

#### Scenario: Preset binds to script thread group step
- **WHEN** a preset references stepId
- **THEN** the stepId SHALL exist in the associated script version as a THREAD_GROUP step

### Requirement: Configuration inheritance
The system SHALL merge plan-level defaults with scenario-level overrides when creating an execution snapshot.

#### Scenario: Scenario inherits plan nodes
- **WHEN** a plan defines controllerNodeId=1 and a scenario has null controllerNodeId
- **THEN** the execution snapshot SHALL use controllerNodeId=1

#### Scenario: Scenario overrides plan monitors
- **WHEN** a plan defines monitorTargetIds=[1,2] and a scenario defines monitorTargetIds=[3]
- **THEN** the execution snapshot SHALL use monitorTargetIds=[3]

#### Scenario: Execution uses script default when no preset selected
- **WHEN** an execution is triggered without threadGroupConfigId
- **THEN** the execution SHALL use the thread group configuration defined in the script's JMX
- **AND** threads, rampUp, duration, loops in ExecutionConfig SHALL be 0

#### Scenario: Execution applies selected preset
- **WHEN** an execution is triggered with threadGroupConfigId
- **THEN** the execution snapshot SHALL record the preset id, stepId, stepName, threads, rampUp, and duration
- **AND** only the target Thread Group step SHALL be patched in the JMX before execution

### Requirement: No plan-wide execution
The system SHALL NOT provide an API to execute an entire plan at once.

#### Scenario: Only scenario execution endpoint
- **WHEN** a client wants to run a test
- **THEN** the client MUST call POST /api/scenarios/{scenarioId}/executions
