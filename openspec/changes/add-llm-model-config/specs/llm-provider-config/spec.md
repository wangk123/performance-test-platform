## ADDED Requirements

### Requirement: Platform LLM provider CRUD
The system SHALL allow managing platform-level LLM providers with name, required default BaseUrl, optional Anthropic BaseUrl, apiKey, enabled flag, and store-body-default flag.

#### Scenario: Create provider with default BaseUrl
- **WHEN** an operator creates a provider with name, baseUrl, and apiKey
- **THEN** the provider SHALL be persisted as enabled by default unless specified otherwise
- **AND** apiKey SHALL NOT be returned in plaintext in list or detail responses
- **AND** responses SHALL indicate whether an apiKey is configured

#### Scenario: Update apiKey only when provided
- **WHEN** an operator updates a provider without sending a new apiKey
- **THEN** the stored apiKey SHALL remain unchanged

#### Scenario: Optional Anthropic BaseUrl
- **WHEN** an operator creates or updates a provider without baseUrlAnthropic
- **THEN** the provider SHALL remain valid
- **AND** Anthropic protocol calls SHALL fall back to the default baseUrl

### Requirement: Cascade delete provider with confirmation
The system SHALL require explicit cascade confirmation before deleting a provider that still has models, and SHALL delete those models when cascade is confirmed.

#### Scenario: Delete provider without models
- **WHEN** an operator deletes a provider that has no models
- **THEN** the provider SHALL be removed

#### Scenario: Delete provider with models without cascade
- **WHEN** an operator deletes a provider that still has models without cascade confirmation
- **THEN** the system SHALL reject the request with a conflict indicating models still exist

#### Scenario: Cascade delete provider and models
- **WHEN** an operator confirms cascade delete for a provider that has models
- **THEN** the provider and all its models SHALL be removed
- **AND** existing call records SHALL remain with provider/model name snapshots

### Requirement: Fetch and import remote model list
The system SHALL support fetching remote model candidates for a provider using a selected apiType and importing selected candidates as models under that provider.

#### Scenario: Fetch models with default OPENAI
- **WHEN** an operator requests fetch-models without specifying apiType
- **THEN** the system SHALL use OPENAI protocol against the provider default baseUrl
- **AND** return a candidate list without persisting models

#### Scenario: Fetch models with ANTHROPIC and fallback BaseUrl
- **WHEN** an operator requests fetch-models with apiType ANTHROPIC and baseUrlAnthropic is empty
- **THEN** the system SHALL call the Anthropic list API using the default baseUrl

#### Scenario: Import selected models
- **WHEN** an operator imports candidates with an apiType
- **THEN** each imported model SHALL belong to that provider with the given apiType
- **AND** an already existing modelName under the same provider SHALL be skipped

### Requirement: Provider connectivity test entry
The system SHALL expose a provider connectivity test endpoint that invokes the gateway and records the call.

#### Scenario: Test with explicit modelId
- **WHEN** an operator posts a connectivity test with modelId under that provider
- **THEN** the system SHALL invoke the gateway with scene TEST_CONNECTION
- **AND** return success or failure with latency and callRecordId

#### Scenario: Test without modelId uses provider default or any enabled model
- **WHEN** an operator posts a connectivity test without modelId
- **THEN** the system SHALL prefer an enabled default model under that provider
- **AND** otherwise use any enabled model under that provider
- **AND** if none exist the system SHALL reject the request as bad request

### Requirement: Model config management navigation
The system SHALL place provider management under System Settings as a child page of "模型配置管理", not as a top-level settings tab sibling to Users/Roles/Permissions.

#### Scenario: Open provider sub-page
- **WHEN** an operator opens 模型配置管理 → 提供商
- **THEN** the provider list and create/edit actions SHALL be available
