# seed-clone

## Purpose

基于已确认模板的批量克隆任务。

## Requirements

### Requirement: Clone job writes batches from a confirmed template
The system SHALL create clone jobs bound to a confirmed template version, a datasource, and a positive clone count N. For each batch 1..N the system MUST build an id map, apply UNIQUE_REGEN / FORMATTED_RAND / BIZ_KEY / FK_REF rewrites, and execute INSERT and UPDATE operations in dependency order. Each batch MUST run in its own transaction. The job MUST record success and failure counts and error details.

#### Scenario: Successful multi-batch clone
- **WHEN** a user starts a clone job with N=3 on a confirmed template against a reachable test datasource
- **THEN** the system attempts three batches and reports per-batch success or failure with final aggregates

#### Scenario: Id map rewrites foreign keys
- **WHEN** a batch inserts a parent row with UNIQUE_REGEN primary key and a child row with FK_REF to that parent
- **THEN** the child row is written using the newly generated parent key from the same batch id map

### Requirement: Clone failure policy is configurable
The system SHALL support failure policies: continue-on-batch-failure (default) and stop-on-first-batch-failure. A failed batch MUST roll back that batch only.

#### Scenario: Continue after one batch fails
- **WHEN** batch 2 fails unique constraint under continue-on-batch-failure
- **THEN** batch 2 is rolled back, the job continues with remaining batches, and the job result marks batch 2 failed

#### Scenario: Stop on first failure
- **WHEN** batch 1 fails under stop-on-first-batch-failure
- **THEN** the job stops without starting subsequent batches and reports failure

### Requirement: Clone jobs enforce limits and audit
The system SHALL reject clone jobs that exceed the configured maximum N or that target an unconfirmed template. The system MUST audit who started the job, template version, N, start/end time, and outcome. Parameter file export is out of scope.

#### Scenario: N exceeds maximum
- **WHEN** a user requests a clone count above the configured maximum
- **THEN** the system rejects the request

#### Scenario: Audit record exists
- **WHEN** a clone job completes
- **THEN** an audit record is available containing operator, template version id, N, and outcome summary
