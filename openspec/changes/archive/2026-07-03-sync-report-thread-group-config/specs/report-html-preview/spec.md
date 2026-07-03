## ADDED Requirements

### Requirement: Gradient comparison matches scenario execution records
The plan report preview page SHALL render the gradient comparison overview using the same per-preset table structure as the task plan scenario execution record panel (`ScenarioRowPanel`).

#### Scenario: Multi thread group preset in gradient overview
- **WHEN** a scenario preset contains two Thread Groups with execution results
- **THEN** the gradient comparison section SHALL display one table block labeled with the preset index
- **AND** the table SHALL list one row per Thread Group with columns: Thread Group name, threads, Ramp-Up, duration, samples, TPS, average response time, and error rate
- **AND** the table SHALL include a summary row aggregating thread count and metrics

#### Scenario: Single thread group preset in gradient overview
- **WHEN** a scenario preset contains one Thread Group
- **THEN** the gradient comparison table SHALL display one row for that Thread Group
- **AND** the table SHALL NOT display a summary row

#### Scenario: Preset without execution data
- **WHEN** a configured preset has no matching finished execution
- **THEN** the gradient comparison table SHALL still show configuration values
- **AND** metric columns SHALL display placeholder dashes

### Requirement: Preset detail section uses per-label charts
The plan report preview page SHALL render time-series charts for each expanded preset using per-interface label metrics, consistent with the execution detail monitoring charts.

#### Scenario: Chart lines per interface
- **WHEN** a user expands a preset with metric series data
- **THEN** the charts SHALL render one line per HTTP sampler label under that preset
- **AND** the charts SHALL NOT render a single merged overall curve

#### Scenario: Chart labels filtered to preset
- **WHEN** execution metric series contains labels outside the preset's Thread Groups
- **THEN** the preset detail charts SHALL only display lines for labels under the preset's configured Thread Groups

### Requirement: Preset detail aggregate table is scoped
The per-preset detail section SHALL display aggregate rows scoped to sampler labels under the preset's configured Thread Groups.

#### Scenario: Scoped aggregate rows in detail
- **WHEN** a user expands a multi-Thread-Group preset detail
- **THEN** the interface aggregate table SHALL list only labels belonging to the preset's configured Thread Groups
