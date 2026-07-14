## ADDED Requirements

### Requirement: Report preview page
The system SHALL provide a dedicated report preview page at route `/projects/:projectId/reports/:executionId` accessible from the execution detail view.

#### Scenario: Navigate from execution detail
- **WHEN** a user clicks "查看报告" on a completed execution detail page
- **THEN** the browser SHALL navigate to the report preview page for that execution

#### Scenario: Direct URL access
- **WHEN** a user directly navigates to the report preview URL
- **THEN** the page SHALL load report data and render the full report

### Requirement: Report summary section
The HTML report SHALL display an executive summary section with key metrics: total samples, throughput (req/s), average response time (ms), P95 response time (ms), and error rate (%).

#### Scenario: Summary display
- **WHEN** the report page loads with valid data
- **THEN** the summary section SHALL show at minimum samples, throughput, average RT, P95, and error rate as styled metric cards

### Requirement: Aggregate report table
The HTML report SHALL display the per-label aggregate report as a sortable table with columns: label, thread group name, samples, average, median, P90, P95, P99, min, max, error rate, and throughput.

#### Scenario: Table rendering
- **WHEN** report data contains aggregate rows
- **THEN** the table SHALL render all rows with formatted values (ms for times, % for error rate, /s for throughput)

#### Scenario: Empty table
- **WHEN** report data contains zero aggregate rows
- **THEN** the table SHALL display an empty state placeholder

### Requirement: Response time over time chart
The HTML report SHALL render a response time over time line chart using the metric series data.

#### Scenario: Chart with data
- **WHEN** metric series data is available
- **THEN** an ECharts line chart SHALL display response time (ms) over the test duration

#### Scenario: Chart without data
- **WHEN** metric series data is empty
- **THEN** the chart area SHALL display a "暂无数据" placeholder

### Requirement: Throughput over time chart
The HTML report SHALL render a throughput over time chart (TPS/requests per second) using the metric series data.

#### Scenario: Throughput chart
- **WHEN** metric series data is available
- **THEN** an ECharts chart SHALL display throughput over the test duration

### Requirement: Error rate over time chart
The HTML report SHALL render an error rate over time chart using the metric series data.

#### Scenario: Error rate chart
- **WHEN** metric series data is available
- **THEN** an ECharts chart SHALL display error rate (%) over the test duration

### Requirement: Rich text editor for report narrative
The report page SHALL include a rich text editor allowing users to add a title, conclusion, notes, and other narrative content to the report.

#### Scenario: Editor initial state
- **WHEN** the report page first loads
- **THEN** the rich text editor SHALL be empty with placeholder text prompting the user to write a conclusion

#### Scenario: Editor content in Word export
- **WHEN** the user clicks "下载 Word" and has content in the editor
- **THEN** the editor content SHALL be included in the Word export request as HTML or structured text

#### Scenario: Editor formatting
- **WHEN** user edits report content
- **THEN** the editor SHALL support bold, italic, headings, bullet lists, and numbered lists

### Requirement: Download Word button
The HTML report page SHALL provide a "下载 Word" button that triggers Word export for the current execution.

#### Scenario: Download triggered
- **WHEN** user clicks "下载 Word" on the report page
- **THEN** the system SHALL capture ECharts chart screenshots, collect editor content, and send an export request to the backend

#### Scenario: Download in progress
- **WHEN** the Word export request is in progress
- **THEN** the download button SHALL show a loading state and be disabled

### Requirement: Report page error state
The report page SHALL handle loading and error states gracefully.

#### Scenario: Loading state
- **WHEN** report data is being fetched
- **THEN** the page SHALL display a loading skeleton or spinner

#### Scenario: Error state
- **WHEN** report data fetch fails
- **THEN** the page SHALL display an error message with a retry button
