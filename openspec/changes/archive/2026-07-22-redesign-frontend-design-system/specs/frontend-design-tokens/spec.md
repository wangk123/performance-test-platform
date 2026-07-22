## ADDED Requirements

### Requirement: Semantic design tokens drive UI surfaces

The frontend SHALL expose a semantic token set for canvas, surface, ink, muted, line, accent, accent-soft, and status colors (ok, warn, danger), applied via CSS custom properties on the document theme root.

#### Scenario: Light theme token application
- **WHEN** the user uses the default (light) theme
- **THEN** UI surfaces MUST use the light semantic tokens (including accent approximately `#0B7F8A`) rather than Ant Design default blue as the brand accent

#### Scenario: Dark theme token application
- **WHEN** the user switches to the dark theme
- **THEN** the same semantic token names MUST map to dark-theme values with readable contrast for text and accent

### Requirement: Typography roles for UI and data

The frontend SHALL define distinct typography roles for interface text and for data/code (monospace), with a documented fallback stack if the primary faces fail to load.

#### Scenario: Data values use monospace role
- **WHEN** monitoring KPIs, chart axes labels that represent metrics, or code editors render numeric/code content
- **THEN** those elements MUST use the data/code typography role

### Requirement: Density modes share the same tokens

The design system SHALL provide density mode B (default) and density mode A (compact) that adjust spacing and control/table density without changing semantic colors.

#### Scenario: Compact density on live monitoring
- **WHEN** a live execution monitoring view is shown
- **THEN** the view MUST use density mode A while remaining on the active color theme

#### Scenario: Default density on configuration pages
- **WHEN** a settings, project list, or seed-configuration page is shown
- **THEN** the view MUST use density mode B

### Requirement: Ant Design theme follows tokens

`a-config-provider` theme configuration SHALL derive primary and related tokens from the design-system accent and algorithm from the active light/dark mode.

#### Scenario: Primary controls match accent
- **WHEN** a primary Ant Design button or active control is rendered
- **THEN** its primary color MUST match the design-system accent token for the active theme
