## ADDED Requirements

### Requirement: JMeter built-in function catalog in HTTP editor

The HTTP request editor variable panel SHALL list a catalog of common JMeter built-in functions in addition to platform functions from `GET /api/jmeter-functions`, and clicking an entry SHALL insert its example syntax into the active HTTP field.

#### Scenario: Display built-in functions section

- **WHEN** a user opens the HTTP editor variable panel
- **THEN** a dedicated section for JMeter built-in functions SHALL be shown alongside the platform functions section

#### Scenario: Insert built-in function example

- **WHEN** a user clicks a JMeter built-in function entry while an HTTP field is active
- **THEN** the editor SHALL insert that entry's example syntax (for example `${__UUID()}`) into the active field

## MODIFIED Requirements

### Requirement: HTTP editor function insertion

The HTTP request editor SHALL provide platform functions and JMeter built-in functions in the variable panel, allowing users to insert `${__funcName(...)}` syntax into all HTTP request parameter fields including URL, Params, Headers, and Body (form and raw).

#### Scenario: Insert function from panel

- **WHEN** a user edits an HTTP request step and opens the variable panel
- **THEN** a「平台函数」section SHALL list available functions from the API
- **AND** a「JMeter 内置函数」section SHALL list common built-in functions from the frontend catalog
- **AND** clicking a function SHALL insert its `example` syntax into the active field

#### Scenario: Function list loaded with editor

- **WHEN** the script editor loads
- **THEN** the platform functions list SHALL be fetched once and cached for the editor session
- **AND** the JMeter built-in catalog SHALL be available without an additional backend API call

#### Scenario: Insert function into raw body field

- **WHEN** the active field is the raw Body editor and the user clicks a platform or built-in function
- **THEN** the function example syntax SHALL be inserted into the raw Body content
