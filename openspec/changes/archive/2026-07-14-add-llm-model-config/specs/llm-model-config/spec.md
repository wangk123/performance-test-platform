## ADDED Requirements

### Requirement: Models belong to providers with api type
The system SHALL manage LLM models under a provider, each with modelName, optional displayName, apiType OPENAI or ANTHROPIC (default OPENAI), enabled flag, and at most one platform-wide default model.

#### Scenario: Create model defaults to OPENAI
- **WHEN** an operator creates a model without specifying apiType
- **THEN** the model SHALL be stored with apiType OPENAI

#### Scenario: Same modelName under different providers
- **WHEN** provider A and provider B each have a model named deepseek-v4-flash
- **THEN** both models SHALL exist with distinct modelIds
- **AND** both SHALL appear in available-models under their own provider groups

#### Scenario: Set platform default model
- **WHEN** an operator sets a model as default
- **THEN** that model SHALL be marked isDefault true
- **AND** any previously default model SHALL no longer be default

#### Scenario: Disable model
- **WHEN** an operator disables a model
- **THEN** the model SHALL NOT appear in available-models

### Requirement: Available models grouped by provider
The system SHALL expose an available-models API that returns only enabled providers and their enabled models, grouped by provider, for other business features to select from.

#### Scenario: Grouped list for business selection
- **WHEN** a client requests available-models
- **THEN** the response SHALL be an array of provider groups each containing providerId, providerName, and models
- **AND** each model entry SHALL include modelId, modelName, displayName, apiType, and isDefault
- **AND** callers MUST select by modelId rather than modelName alone

#### Scenario: Disabled provider excluded
- **WHEN** a provider is disabled
- **THEN** that provider and its models SHALL NOT appear in available-models

### Requirement: Model management UI sub-page
The system SHALL provide a Models sub-page under 模型配置管理 for listing, creating, editing, setting default, and deleting models.

#### Scenario: Filter models by provider
- **WHEN** an operator opens the Models sub-page and selects a provider filter
- **THEN** only models under that provider SHALL be listed
