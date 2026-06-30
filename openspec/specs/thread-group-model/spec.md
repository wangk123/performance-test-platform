# thread-group-model

## Purpose

后端线程组强类型模型与步骤类型枚举，替代无类型的 `Map<String, Object>` 和字符串比较，为脚本编辑功能的类型安全和可扩展性奠定基础。

## Requirements

### Requirement: ThreadGroupConfig strong-typed record
The system SHALL provide a `ThreadGroupConfig` record with fields `threads` (int), `rampUp` (int), `loops` (int), `duration` (int) to represent thread group configuration, replacing the untyped `Map<String, Object>` for THREAD_GROUP steps.

#### Scenario: Parser creates ThreadGroupConfig from JMX
- **WHEN** `JmeterScriptParser` parses a `<ThreadGroup>` element from a JMX file
- **THEN** the resulting `ScriptStepDefinition` SHALL provide a `ThreadGroupConfig` with values extracted from `ThreadGroup.num_threads`, `ThreadGroup.ramp_time`, `LoopController.loops`, and `ThreadGroup.duration` XML attributes

#### Scenario: Renderer reads ThreadGroupConfig for JMX generation
- **WHEN** `JmeterScriptRenderer` renders a THREAD_GROUP step to JMX XML
- **THEN** the renderer SHALL use the `ThreadGroupConfig` values to populate the corresponding XML attributes

#### Scenario: Round-trip serialization preserves values
- **WHEN** a JMX file is parsed into steps and then rendered back to JMX
- **THEN** the thread group configuration values (threads, rampUp, loops, duration) SHALL be identical to the original

### Requirement: ScriptStepType enumeration
The system SHALL provide a `ScriptStepType` enum containing all supported step types, with each enum value corresponding to a valid step type string.

#### Scenario: Enum values match existing type strings
- **WHEN** code calls `ScriptStepType.valueOf("THREAD_GROUP")`
- **THEN** the result SHALL be the `THREAD_GROUP` enum constant

#### Scenario: Step type validation
- **WHEN** a `ScriptStepDefinition` has an unknown type string
- **THEN** calling `stepType()` SHALL return `null` or throw an informative exception, not silently proceed

#### Scenario: Parser uses enum for type checking
- **WHEN** `JmeterScriptParser` identifies XML elements to parse
- **THEN** it SHALL use `ScriptStepType` enum values for type determination instead of string literals

### Requirement: ThreadGroupConfig default values
The system SHALL provide sensible default values when creating a new thread group: threads=1, rampUp=0, loops=1, duration=0.

#### Scenario: New thread group from UI
- **WHEN** a user creates a new THREAD_GROUP step via the step creation dialog
- **THEN** the thread group SHALL be initialized with threads=1, rampUp=0, loops=1, duration=0

#### Scenario: New thread group from API
- **WHEN** a THREAD_GROUP step is created without explicit config values
- **THEN** missing values SHALL default to threads=1, rampUp=0, loops=1, duration=0
