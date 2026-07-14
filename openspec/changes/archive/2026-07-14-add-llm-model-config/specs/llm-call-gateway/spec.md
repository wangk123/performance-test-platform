## ADDED Requirements

### Requirement: Unified LLM gateway
The system SHALL expose a single LlmGateway invoke path that resolves model and provider, selects an adapter by model apiType, and writes a call record for both success and failure.

#### Scenario: Invoke OPENAI model
- **WHEN** a caller invokes the gateway with an enabled model whose apiType is OPENAI
- **THEN** the system SHALL call the OpenAI-compatible chat endpoint using the provider default baseUrl and apiKey
- **AND** SHALL persist a call record with status SUCCESS or FAILED

#### Scenario: Invoke ANTHROPIC model with optional BaseUrl
- **WHEN** a caller invokes the gateway with an enabled model whose apiType is ANTHROPIC
- **THEN** the system SHALL use baseUrlAnthropic if configured, otherwise the provider default baseUrl
- **AND** SHALL use the Anthropic adapter

#### Scenario: Reject disabled model or provider
- **WHEN** the model or its provider is disabled
- **THEN** the gateway SHALL reject the invoke without calling the external API

### Requirement: Protocol adapters without third-party LLM SDK
The system SHALL implement OpenAI-compatible and Anthropic adapters using HTTP clients, each supporting chat and listModels operations.

#### Scenario: OpenAI list models
- **WHEN** fetch-models runs with apiType OPENAI
- **THEN** the OpenAI adapter SHALL request the provider models list endpoint and return candidate model ids

#### Scenario: Anthropic chat
- **WHEN** the gateway invokes an ANTHROPIC model
- **THEN** the Anthropic adapter SHALL send a Messages API request using the selected BaseUrl and apiKey

### Requirement: Call record metadata and optional bodies
The system SHALL persist call records with provider/model identifiers and name snapshots, apiType, scene, status, latency, optional token counts, optional error message, triggeredBy, and createdAt. Request and response bodies SHALL be stored only when storeBody is enabled.

#### Scenario: Metadata always recorded
- **WHEN** a gateway invoke completes successfully or fails
- **THEN** a call record SHALL exist with metadata fields and SHALL NOT contain the apiKey

#### Scenario: Body storage follows switch
- **WHEN** storeBody is true or provider store_body_default is true and no override disables it
- **THEN** the call record MAY include request and response body text subject to a size truncation limit
- **WHEN** storeBody resolves to false
- **THEN** request and response bodies SHALL be null

#### Scenario: Query call records
- **WHEN** an operator opens 模型配置管理 → 调用记录 with optional filters
- **THEN** the system SHALL return a paginated list filterable by providerId, modelId, scene, and status

### Requirement: Connectivity test uses gateway
The system SHALL implement provider connectivity tests exclusively through LlmGateway with scene TEST_CONNECTION.

#### Scenario: Successful DeepSeek-style OpenAI test
- **WHEN** an operator configures an OpenAI-compatible provider and model and runs connectivity test
- **THEN** the gateway SHALL complete a short chat invoke
- **AND** a call record with scene TEST_CONNECTION and status SUCCESS SHALL be queryable
