## ADDED Requirements

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
