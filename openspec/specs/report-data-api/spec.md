# report-data-api

## Purpose

性能测试报告数据聚合 API，为 HTML 预览与 Word 导出提供单次请求所需的执行元数据、聚合报告、时序指标、失败样本与脚本配置。

## Requirements

### Requirement: Report data aggregation endpoint
The system SHALL provide `GET /api/reports/{executionId}/data` that returns all data needed to render a performance test report in a single response.

#### Scenario: Successful data retrieval
- **WHEN** a user requests report data for a completed execution
- **THEN** the response SHALL include execution metadata, aggregate report summary and rows, metric time series, failure samples summary, target monitoring snapshots, and script configuration

#### Scenario: Execution not found
- **WHEN** a user requests report data for a non-existent execution ID
- **THEN** the system SHALL return HTTP 404

#### Scenario: Live execution data
- **WHEN** a user requests report data for a currently RUNNING execution
- **THEN** the response SHALL include live aggregate data (accuracy=live) and the latest available metric series

### Requirement: Report data includes execution metadata
The response SHALL include execution metadata: execution ID, scenario name, script version name, execution status, start time, duration, and error message if failed.

#### Scenario: Successful execution metadata
- **WHEN** report data is requested for a SUCCESS execution
- **THEN** the metadata SHALL contain status=SUCCESS and a null error message

#### Scenario: Failed execution metadata
- **WHEN** report data is requested for a FAILED execution
- **THEN** the metadata SHALL contain status=FAILED and the error message

### Requirement: Report data includes aggregate report
The response SHALL include the aggregate report: summary (samples, throughput, avgRt, p95, errorRate, accuracy) and per-label aggregate rows.

#### Scenario: Aggregate data with final accuracy
- **WHEN** report data is requested for a completed execution with stored aggregate report
- **THEN** the aggregate data SHALL be loaded from the persistent store

#### Scenario: Aggregate data with live accuracy
- **WHEN** report data is requested for a running execution without persistent report
- **THEN** the aggregate data SHALL be loaded from the live cache with accuracy=live

### Requirement: Report data includes metric time series
The response SHALL include metric time series data (active threads, response time, throughput, errors over time) for chart rendering.

#### Scenario: Metric series available
- **WHEN** report data is requested and metric series records exist for the execution
- **THEN** the response SHALL include time series data as arrays of timestamp-value pairs

#### Scenario: Metric series unavailable
- **WHEN** report data is requested and no metric series records exist
- **THEN** the metric series field SHALL be an empty array

### Requirement: Report data includes failure samples summary
The response SHALL include a failure summary with error count and up to 100 most recent failure samples with request/response details.

#### Scenario: No failures
- **WHEN** report data is requested and no failure samples exist
- **THEN** the failure summary SHALL show errorCount=0 and empty samples array

#### Scenario: Failure samples capped
- **WHEN** report data is requested and more than 100 failure samples exist
- **THEN** the response SHALL include only the first 100 samples and indicate that results are truncated

### Requirement: Report data includes script configuration
The response SHALL include the test configuration: thread group name, thread count, ramp-up time, and duration settings from the original script version.

#### Scenario: Script version available
- **WHEN** report data is requested and the associated script version exists
- **THEN** the config SHALL include thread group parameters extracted from the script definition

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
