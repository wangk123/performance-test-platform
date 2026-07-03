## ADDED Requirements

### Requirement: Plan report groups data by thread group preset
The system SHALL provide `GET /api/reports/plans/{planId}/data` where each scenario report contains a `presets` array instead of a flat `rounds` list. Each preset SHALL correspond to one `sortOrder` group from the scenario's stored `threadGroupConfigs`.

#### Scenario: Scenario with multiple presets
- **WHEN** a scenario has thread group configs with sortOrder 0 and 1
- **THEN** the scenario report SHALL contain two preset entries ordered by sortOrder ascending

#### Scenario: Scenario with no thread group configs
- **WHEN** a scenario has an empty `threadGroupConfigs` list
- **THEN** the scenario report SHALL contain an empty `presets` array

### Requirement: Plan report includes only configured presets
The system SHALL include only executions that match a currently configured preset. Executions for removed or unmatched presets SHALL NOT appear in the report.

#### Scenario: Execution matches current preset
- **WHEN** a finished execution's config has `threadGroupPresetSortOrder` equal to a configured preset sortOrder
- **THEN** that execution SHALL be eligible for that preset's report data

#### Scenario: Execution for removed preset
- **WHEN** a finished execution's config references a sortOrder no longer present in the scenario's thread group configs
- **THEN** that execution SHALL NOT appear in the plan report

### Requirement: Plan report uses latest execution per preset
For each preset, the system SHALL use only the most recent finished execution that matches that preset.

#### Scenario: Multiple executions for same preset
- **WHEN** a preset has three finished matching executions
- **THEN** the report SHALL use metrics from the execution with the highest id

#### Scenario: Preset with no matching execution
- **WHEN** a preset is configured but has no finished matching execution
- **THEN** the preset SHALL still appear with configuration rows and dash placeholders for metrics

### Requirement: Plan report includes per-thread-group rows and summary
Each preset SHALL include a `rows` array with one entry per configured Thread Group. When a preset contains more than one Thread Group, the preset SHALL also include a `summary` object aggregating metrics across those rows, consistent with task plan scenario execution records.

#### Scenario: Single thread group preset
- **WHEN** a preset contains one Thread Group configuration row
- **THEN** the preset SHALL contain one row with scoped metrics and SHALL NOT include a summary object

#### Scenario: Multiple thread group preset
- **WHEN** a preset contains two Thread Group configuration rows and a matching execution with aggregate data
- **THEN** the preset SHALL contain two rows with per-Thread-Group scoped metrics
- **AND** the preset SHALL contain a summary object with aggregated samples, throughput, average response time, and error rate

#### Scenario: Per-thread-group metric scoping
- **WHEN** aggregate report data is scoped for a Thread Group row
- **THEN** metrics SHALL be calculated from sampler labels belonging to that Thread Group in the script definition

### Requirement: Plan report metric series uses per-label data
The plan report SHALL include metric time series using per-label (`tick.labels`) data filtered to sampler labels under the preset's configured Thread Groups. The report SHALL NOT use `tick.overall` as the chart data source.

#### Scenario: Metric series with multiple interfaces
- **WHEN** a preset execution has metric ticks with labels for multiple HTTP samplers
- **THEN** the preset metric series SHALL include separate label metrics for each sampler under the preset's Thread Groups

#### Scenario: Metric series excludes unrelated labels
- **WHEN** execution aggregate data contains sampler labels not under the preset's configured Thread Groups
- **THEN** those labels SHALL NOT appear in the preset's metric series or scoped aggregate rows
