## ADDED Requirements

### Requirement: System infers field roles from samples and metadata
After capture finishes, the system SHALL infer a draft seed template from multi-sample diffs and database metadata (primary keys, unique indexes, foreign keys). Each column in an operation MUST be assigned a role from: LITERAL, UNIQUE_REGEN, FK_REF, BIZ_KEY, FORMATTED_RAND, TIMESTAMP, UPDATE_KEY, UPDATE_SET, IGNORE. Confidence MUST be one of HIGH, MEDIUM, LOW with a human-readable rationale. Executable operations MUST be INSERT or UPDATE only.

#### Scenario: Stable value becomes LITERAL with HIGH confidence
- **WHEN** a column value is identical across all samples and is not classified as an association key
- **THEN** the draft template assigns role LITERAL with HIGH confidence and a rationale citing sample stability

#### Scenario: Unique changing column becomes UNIQUE_REGEN
- **WHEN** a column value differs in every sample and the column participates in a unique index
- **THEN** the draft template assigns UNIQUE_REGEN with HIGH confidence

#### Scenario: Formal FK becomes FK_REF
- **WHEN** metadata defines a foreign key from column A to table B
- **THEN** the draft template assigns FK_REF to A pointing at B with HIGH confidence

### Requirement: Template confirmation is a hard gate
The system SHALL NOT allow clone jobs against an unconfirmed template. Confirmation MUST persist confirmer identity and timestamp and produce an immutable template version. LOW confidence roles MUST be changed or explicitly accepted before confirmation. UNIQUE_REGEN and FORMATTED_RAND columns MUST have a bound generator before confirmation succeeds.

#### Scenario: Clone blocked before confirm
- **WHEN** a user attempts to create a clone job on a draft (unconfirmed) template
- **THEN** the system rejects the request

#### Scenario: Confirm requires generators
- **WHEN** a user confirms a template that still has UNIQUE_REGEN without a generator
- **THEN** the system rejects confirmation with a validation error

#### Scenario: Confirmed version is immutable
- **WHEN** a template version has been confirmed
- **THEN** subsequent role edits create a new draft or new version and do not mutate the confirmed version used by existing jobs

### Requirement: Generators can bind to platform format helpers
The system SHALL allow FORMATTED_RAND and UNIQUE_REGEN columns to bind generators that produce values satisfying common formats (at least mobile and id-card class helpers aligned with the platform function library semantics). Unrecognized formats MUST require the user to pick a generator on the confirm UI.

#### Scenario: Mobile-like column suggests mobile generator
- **WHEN** inferred morphology matches a mobile number pattern across samples
- **THEN** the draft suggests a mobile generator binding that the user can confirm or change
