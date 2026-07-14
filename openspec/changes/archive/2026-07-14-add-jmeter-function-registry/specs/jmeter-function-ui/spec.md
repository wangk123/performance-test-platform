## ADDED Requirements

### Requirement: Function library read-only page

The project function library tab SHALL display a read-only list of platform JMeter functions fetched from `GET /api/jmeter-functions`, replacing the current placeholder content.

#### Scenario: Display function list

- **WHEN** a user navigates to the project functions tab
- **THEN** the page SHALL show a table with function display name, category, description, parameters, and example syntax
- **AND** the page SHALL NOT provide create, edit, or delete controls

#### Scenario: Download function package

- **WHEN** a user clicks the download function package action on the functions page
- **THEN** the browser SHALL download `perftest-jmeter-functions.jar` from `GET /api/jmeter-functions/download`

#### Scenario: Local execution guidance

- **WHEN** the functions page is displayed
- **THEN** the page SHALL inform users that platform execution is distributed-only
- **AND** local JMeter usage requires exporting the script and installing the function JAR

### Requirement: HTTP editor function insertion

The HTTP request editor SHALL provide a platform functions section in the variable panel, allowing users to insert `${__funcName(...)}` syntax into HTTP fields.

#### Scenario: Insert function from panel

- **WHEN** a user edits an HTTP request step and opens the variable panel
- **THEN** a「平台函数」section SHALL list available functions from the API
- **AND** clicking a function SHALL insert its `example` syntax into the active field

#### Scenario: Function list loaded with editor

- **WHEN** the script editor loads
- **THEN** the platform functions list SHALL be fetched once and cached for the editor session

### Requirement: HTTP debug does not execute functions

HTTP debug in the script editor SHALL NOT attempt to evaluate `${__funcName(...)}` calls. Only `${variable}` substitution SHALL be performed during debug.

#### Scenario: Debug with function syntax in URL

- **WHEN** a user runs HTTP debug on a request containing `${__randomMobile()}`
- **THEN** the debug request SHALL send the literal unresolved syntax or skip function evaluation
- **AND** the UI SHALL NOT claim the function was executed during debug
