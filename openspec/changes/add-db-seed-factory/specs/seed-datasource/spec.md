## ADDED Requirements

### Requirement: Project can manage test MySQL datasources
The system SHALL allow a project to create, update, list, delete, and connectivity-test MySQL JDBC datasources used only for seed capture and clone. Credentials MUST be stored encrypted. Fine-grained role checks are out of scope for this change; project access follows existing project visibility.

#### Scenario: Create datasource
- **WHEN** a user creates a datasource with host, port, database, username, and password for a project
- **THEN** the system persists it under that project and returns its id without exposing the raw password in list responses

#### Scenario: Test connectivity
- **WHEN** a user requests a connectivity test for a datasource
- **THEN** the system attempts a JDBC connection and returns success or a failure reason

### Requirement: Capture scope uses include and exclude filters
The system SHALL require a capture filter before recording. Filters MUST support exact `database.table` names and expression matchers (wildcard and/or regex as implemented), with both include and exclude lists. Exclude MUST take precedence over include. An empty include list MUST be rejected.

#### Scenario: Hybrid filter evaluation
- **WHEN** include contains expression `app.order_*` and exclude contains exact `app.order_audit`
- **THEN** the capture table set includes matching `order_*` tables except `app.order_audit`

#### Scenario: Empty include rejected
- **WHEN** a user starts a capture session with no include entries
- **THEN** the system rejects the request with a validation error
