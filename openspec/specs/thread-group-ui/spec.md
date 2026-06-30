# thread-group-ui

## Purpose

前端线程组 UI 重构，消除冗余 `threadGroups[]` 数据字段，统一从 `steps` 实时派生；抽取独立组件（ThreadGroupEditor、ThreadGroupSummary）降低编辑器复杂度；支持任务配置时从多线程组中选择。

## Requirements

### Requirement: Eliminate redundant threadGroups field
The `ScriptAsset` interface SHALL NOT contain a `threadGroups` field. All thread group information SHALL be derived from the `steps` array in real time.

#### Scenario: ScriptAsset after API response mapping
- **WHEN** `mapScriptDefinition()` processes a `ScriptDefinition` from the backend
- **THEN** the resulting `ScriptAsset` SHALL NOT contain a `threadGroups` property

#### Scenario: Components access thread group data
- **WHEN** a component needs thread group information (e.g., workspace summary, task config)
- **THEN** it SHALL use the `useThreadGroups(steps)` composable to derive the data reactively

### Requirement: useThreadGroups composable
The system SHALL provide a `useThreadGroups(steps)` composable that computes thread group data from the steps array, returning a reactive list of `{ name, threads, rampUp, loops, duration }`.

#### Scenario: Steps with thread groups
- **WHEN** steps contain two THREAD_GROUP steps with different configurations
- **THEN** `useThreadGroups(steps).threadGroups` SHALL return an array of two objects with the correct name, threads, rampUp, loops, and duration values

#### Scenario: Steps with no thread groups
- **WHEN** steps contain no THREAD_GROUP steps
- **THEN** `useThreadGroups(steps).threadGroups` SHALL return an empty array

#### Scenario: Steps edited in editor
- **WHEN** a user modifies a THREAD_GROUP step's config in the editor
- **THEN** `useThreadGroups(steps).threadGroups` SHALL reactively update to reflect the new values

### Requirement: TaskConfigDialog thread group selection
`TaskConfigDialog` SHALL allow the user to select which thread group's parameters to use for task execution when the script contains multiple thread groups.

#### Scenario: Script with one thread group
- **WHEN** the task dialog opens for a script with exactly one thread group
- **THEN** the thread group parameters SHALL be auto-filled and the selector SHALL show the single thread group name

#### Scenario: Script with multiple thread groups
- **WHEN** the task dialog opens for a script with multiple thread groups
- **THEN** a dropdown SHALL display all thread group names, and selecting one SHALL populate threads, rampUp, duration, and loops fields

#### Scenario: Script with no thread groups
- **WHEN** the task dialog opens for a script with no thread groups
- **THEN** the execution parameter fields SHALL be empty or disabled, and the user SHALL NOT be able to submit the task

### Requirement: ThreadGroupEditor component
The system SHALL provide a standalone `ThreadGroupEditor.vue` component that renders the four thread group configuration fields (threads, rampUp, loops, duration) with appropriate labels, input types, and validation.

#### Scenario: Editing thread group config
- **WHEN** a user selects a THREAD_GROUP step in the editor
- **THEN** `StepDetail.vue` SHALL render `ThreadGroupEditor` with the step's config values
- **AND** changes in the editor SHALL update the step's config reactively

#### Scenario: Field validation
- **WHEN** a user enters a negative value for threads
- **THEN** the editor SHALL display a validation error and prevent saving

### Requirement: ThreadGroupSummary component
The system SHALL provide a `ThreadGroupSummary.vue` component that renders a concise text summary of a thread group (e.g., "100 线程 · Ramp 60s · 600s") for use in step sidebar and workspace views.

#### Scenario: Sidebar display
- **WHEN** `StepSidebar.vue` renders a THREAD_GROUP step
- **THEN** it SHALL use `ThreadGroupSummary` to display the thread group's parameter summary

#### Scenario: Workspace display
- **WHEN** `ScriptWorkspace.vue` shows script asset information
- **THEN** it SHALL use `useThreadGroups` to display thread group count and summaries
