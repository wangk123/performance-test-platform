## ADDED Requirements

### Requirement: Standard business page pattern

List, overview, settings, seed, and similar configuration pages SHALL follow a shared pattern: page title row with primary actions, optional single-line filters/toolbar, then surface panels for tables/forms—without large decorative hero bands or multi-color gradient hero blocks.

#### Scenario: Project list uses standard pattern
- **WHEN** the project list page is rendered
- **THEN** it MUST show a title/action header and table/list surface without a full-width marketing hero

#### Scenario: Home retains information hierarchy without hero spectacle
- **WHEN** the home/workbench page is rendered
- **THEN** summary metrics, recent projects, and workflow entry points MUST remain available, presented with the standard density-B pattern rather than a large gradient hero

### Requirement: Live monitoring page pattern

Live execution monitoring views SHALL use density A and present, in order where data exists: summary KPIs, aggregate sampler table, TPS chart, response-time chart, target server resource charts, JVM charts, and exception samples—without large empty placeholder regions above the charts.

#### Scenario: Charts occupy the monitoring content area
- **WHEN** an execution has monitoring series and/or target metrics
- **THEN** the corresponding chart panels MUST be visible in the monitoring content area without a vacant hero spacer above them

#### Scenario: Project monitor configuration stays configuration-pattern
- **WHEN** the project monitoring target configuration page is rendered
- **THEN** it MUST use the standard business page pattern (density B), not the live monitoring chart pattern

### Requirement: Auth screen uses product tokens

The authentication screen SHALL use the same design tokens and accent as the shell, presented as a focused sign-in surface rather than a marketing landing page.

#### Scenario: Auth matches accent and surfaces
- **WHEN** an unauthenticated user opens the auth screen
- **THEN** the screen MUST use design-system surface/accent tokens consistent with the rest of the product

### Requirement: Report preview aligns visually

HTML report preview SHALL use the design-system typography and color tokens for headings, tables, and charts so it reads as the same product family as the app shell.

#### Scenario: Report preview token consistency
- **WHEN** a report preview page is rendered
- **THEN** primary text, muted text, borders, and chart accents MUST follow the design-system tokens (light theme default for printable reading unless dark mode is explicitly applied)

### Requirement: No business behavior regression from restyle

Page restyles MUST preserve existing routes, permissions, form fields, API calls, and user-visible business actions; only presentation and shell chrome may change.

#### Scenario: Existing navigation targets remain
- **WHEN** a user uses global or project navigation after the redesign
- **THEN** the same destinations and module capabilities MUST remain reachable
