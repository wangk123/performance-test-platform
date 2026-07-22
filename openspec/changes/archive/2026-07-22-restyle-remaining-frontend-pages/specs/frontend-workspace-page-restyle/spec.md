## ADDED Requirements

### Requirement: Script workspace follows standard page pattern

The script management workspace SHALL use density-B page-head and panel layout without decorative module indexes (for example `Module 03`) as primary navigation chrome.

#### Scenario: Script workspace header
- **WHEN** the user opens the project scripts module
- **THEN** the page MUST show a clear Chinese title/action header and script list/detail surfaces aligned to design tokens

### Requirement: Task plan pages follow standard page pattern

Task plan list, plan detail, and scenario detail views SHALL use the same density-B page pattern and MUST NOT rely on decorative English eyebrows as the sole section identity.

#### Scenario: Task plan list restyle
- **WHEN** the user opens task plans for a project
- **THEN** list and detail chrome MUST match the redesigned panel/header styling while preserving existing plan/scenario/execution actions

### Requirement: Seed factory surfaces follow standard page pattern

Seed factory and seed-capture related panels SHALL share the standard business page pattern (headers, panels, filters) and token colors.

#### Scenario: Seed factory entry
- **WHEN** the user opens the project data/seed factory module
- **THEN** the visible panels MUST use design-system surfaces and headers consistent with other project modules

### Requirement: Function library and members pages align

Function library and project members views SHALL remove leftover prototype decoration and use standard headers/panels.

#### Scenario: Function library header
- **WHEN** the function library page is rendered
- **THEN** it MUST present a standard page or panel header without requiring decorative eyebrow text for understanding

### Requirement: Dialogs and drawers use token chrome

Modal dialogs and drawers used for script import/create, script params, scenarios, and related flows SHALL use design-system borders, spacing, and accent for primary actions without changing field sets or submit behavior.

#### Scenario: Dialog visual consistency
- **WHEN** a user opens a script or scenario dialog
- **THEN** the dialog chrome MUST match the redesign tokens and MUST still expose the same actionable fields

### Requirement: Script editor internals align with workbench

Step sidebar and step detail inside the script editor SHALL use token spacing/typography and MUST preserve save, import, and step-editing actions.

#### Scenario: Editor internal chrome
- **WHEN** the script editor workbench is open
- **THEN** step tree and detail panes MUST visually align with the workbench top bar already redesigned

### Requirement: Dark theme readability for remaining pages

Remaining workspace pages and dialogs SHALL remain readable in dark theme using semantic tokens.

#### Scenario: Dark theme workspace page
- **WHEN** dark theme is active and the user opens scripts, task plans, or seed factory
- **THEN** text, borders, and panels MUST remain legible without reverting to light-only hard-coded colors

### Requirement: Visual acceptance against redesign mockup

Shell and key workspace pages SHALL be visually checked against `design-mockup-redesign.html` for dual-rail structure and density intent before considering the restyle complete.

#### Scenario: Mockup comparison gate
- **WHEN** implementation of remaining pages is finished
- **THEN** dual-rail presence and page-head/panel density MUST be confirmed against the mockup reference
