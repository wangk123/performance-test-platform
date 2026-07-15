## ADDED Requirements

### Requirement: Capture session records multi-sample table diffs
The system SHALL support a capture session bound to a project datasource and an evaluated capture table set. V1 MUST implement a SNAPSHOT capture provider that takes a baseline snapshot of tables in scope, then on each sample signal computes inserts and updates relative to the previous baseline or agreed baseline strategy, storing each sample diff. DELETE cloning is out of scope; delete diffs MAY be ignored or recorded only for diagnostics without entering executable templates.

#### Scenario: Multi-sample recording
- **WHEN** a user starts capture, completes a business action externally, signals sample end, and repeats at least once before finishing the session
- **THEN** the system stores multiple sample diffs for the filtered table set

#### Scenario: Snapshot provider is default
- **WHEN** a capture session is created without an explicit provider
- **THEN** the system uses SNAPSHOT

### Requirement: Capture provider is pluggable
The system SHALL model capture providers so BINLOG can be added later. V1 MUST reject or leave unimplemented any request to run BINLOG capture with a clear unsupported error.

#### Scenario: Binlog not available in V1
- **WHEN** a user requests capture with provider BINLOG
- **THEN** the system returns an unsupported error and does not start recording

### Requirement: Tables without primary keys are marked risky
The system SHALL detect tables in the capture set that lack a primary key. Such tables MUST be flagged as risky and MUST NOT be included in automatic clone plans unless explicitly handled in a later change; V1 MAY surface them on the template for awareness but MUST NOT auto-generate executable INSERT/UPDATE ops for them.

#### Scenario: No-PK table flagged
- **WHEN** a captured table has no primary key
- **THEN** the inferred template marks it as risky and excludes it from executable operations
