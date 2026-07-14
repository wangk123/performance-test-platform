## MODIFIED Requirements

### Requirement: Configuration inheritance
The system SHALL merge plan-level defaults with scenario-level overrides when creating an execution snapshot. Execution parameters (threads, rampUp, duration, loops) SHALL NOT be derived from the scenario and SHALL use the script's native ThreadGroup configuration.

#### Scenario: Scenario inherits plan nodes
- **WHEN** a plan defines controllerNodeId=1 and a scenario has null controllerNodeId
- **THEN** the execution snapshot SHALL use controllerNodeId=1

#### Scenario: Scenario overrides plan monitors
- **WHEN** a plan defines monitorTargetIds=[1,2] and a scenario defines monitorTargetIds=[3]
- **THEN** the execution snapshot SHALL use monitorTargetIds=[3]

#### Scenario: Execution uses script thread configuration
- **WHEN** an execution is triggered for a scenario
- **THEN** the execution SHALL use the thread group configuration defined in the script's JMX
- **AND** the execution SHALL NOT override threads, rampUp, duration, or loops via `-J` properties

### Requirement: Three-layer task model
The system SHALL model performance testing as TaskPlan → TaskScenario → ScenarioExecution.

#### Scenario: Plan contains multiple scenarios
- **WHEN** a user creates a task plan with two scenarios bound to different script versions
- **THEN** both scenarios SHALL be listed under the plan and each SHALL reference exactly one script version

#### Scenario: Scenario produces multiple executions
- **WHEN** a user triggers execution on the same scenario three times
- **THEN** three ScenarioExecution records SHALL exist and all SHALL remain queryable

#### Scenario: Scenario does not store thread parameters
- **WHEN** a scenario is created or updated
- **THEN** the scenario SHALL NOT store thread count, ramp-up, duration, or loops configuration
- **AND** thread configuration SHALL be sourced exclusively from the associated script's ThreadGroup
