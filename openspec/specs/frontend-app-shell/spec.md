# frontend-app-shell

## Purpose

双轨导航与应用壳。

## Requirements

### Requirement: Dual-rail global navigation

The application shell SHALL provide a persistent narrow global rail with brand mark and icon entries for global destinations (home, projects, execution nodes, settings, LLM config, and other existing global nav items).

#### Scenario: Global destinations from rail
- **WHEN** the user is signed in and not inside a project context
- **THEN** the global rail MUST remain visible and selecting a rail item MUST navigate using the existing navigation behavior

#### Scenario: No decorative nav indexes
- **WHEN** global or project navigation items are rendered in the shell
- **THEN** they MUST NOT display decorative numeric indexes such as `01` / `02`

### Requirement: Project context rail

When a project is active, the shell SHALL show a secondary context rail listing that project's modules; when no project is active, the context rail MUST be hidden so the main content can use the remaining width.

#### Scenario: Entering a project reveals context rail
- **WHEN** the user enters a project
- **THEN** the context rail MUST appear with the project identity and module links for existing project tabs

#### Scenario: Leaving a project hides context rail
- **WHEN** the user returns to a global destination with no current project
- **THEN** the context rail MUST be hidden

### Requirement: Top bar stays secondary

The top bar SHALL show breadcrumb (or equivalent location) context and user/theme actions, and MUST NOT be the primary place for stacking page-level CTAs that belong in the content header.

#### Scenario: Theme toggle remains available
- **WHEN** the user opens the shell top bar actions
- **THEN** they MUST be able to switch between light and dark themes as today

### Requirement: Fullscreen workbench for script editor

The script editor experience SHALL be presentable as a workbench that can collapse or hide the project context rail to maximize editing space, while preserving a clear path to exit the editor.

#### Scenario: Editor maximizes editing surface
- **WHEN** the user opens the script editor page
- **THEN** the layout MUST prioritize the editor panes and MUST provide an exit/back control to leave the workbench

### Requirement: Shell chrome noise removed

The shell MUST NOT show the previous sidebar footnote card describing backend execution mode as persistent chrome.

#### Scenario: No backend-mode note in shell
- **WHEN** the application shell is rendered
- **THEN** the persistent sidebar note about JMeter/backend mode MUST NOT appear
