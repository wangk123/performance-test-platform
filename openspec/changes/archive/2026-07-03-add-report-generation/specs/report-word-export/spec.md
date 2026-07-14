## ADDED Requirements

### Requirement: Word export endpoint
The system SHALL provide `POST /api/reports/{executionId}/export/word` that accepts chart images and editor content, and returns a downloadable .docx file.

#### Scenario: Successful export
- **WHEN** a valid export request is received with chart images and editor content
- **THEN** the system SHALL return a .docx file with Content-Type `application/vnd.openxmlformats-officedocument.wordprocessingml.document`

#### Scenario: Execution not found
- **WHEN** export is requested for a non-existent execution ID
- **THEN** the system SHALL return HTTP 404

#### Scenario: Template not found
- **WHEN** the default Word template file is missing from the classpath
- **THEN** the system SHALL return HTTP 500 with an appropriate error message

### Requirement: Word document content
The generated Word document SHALL include the following sections in order: report title, test configuration summary, executive summary (key metrics), aggregate report table, chart images, user-edited narrative content, and generation timestamp.

#### Scenario: Complete document structure
- **WHEN** the Word document is generated with all optional content provided
- **THEN** the document SHALL contain all sections with appropriate headings and formatting

#### Scenario: Document without user content
- **WHEN** the editor content is empty
- **THEN** the user narrative section SHALL be omitted from the document

#### Scenario: Document without chart images
- **WHEN** no chart images are provided in the export request
- **THEN** the chart section SHALL be omitted from the document

### Requirement: Chart images embedded in Word
The Word document SHALL embed chart screenshots as inline images within the charts section at appropriate resolution (2x pixel ratio recommended).

#### Scenario: Charts embedded as PNG
- **WHEN** chart images are provided as Base64-encoded PNG strings
- **THEN** each chart image SHALL be decoded and embedded into the document with a descriptive caption

### Requirement: Aggregate report table in Word
The Word document SHALL include the aggregate report as a formatted table with all columns matching the HTML preview.

#### Scenario: Table with data rows
- **WHEN** aggregate rows exist
- **THEN** each row SHALL be rendered as a Word table row with alternating row shading for readability

#### Scenario: Table with no rows
- **WHEN** aggregate rows are empty
- **THEN** the table SHALL display a single row with "暂无数据"

### Requirement: Default Word template
The system SHALL include a default Word template bundled as a classpath resource that defines document styles, page layout, and placeholder positions.

#### Scenario: Default template loaded
- **WHEN** the application starts
- **THEN** the default template SHALL be accessible from the classpath at `templates/report/word-template.docx`

### Requirement: Export request payload
The export endpoint SHALL accept a JSON payload with chart images (as Base64 data URIs) and editor content (as HTML string).

#### Scenario: Valid request payload
- **WHEN** the request body contains `chartImages` (object of name-to-dataURI) and `editorContent` (HTML string)
- **THEN** the request SHALL be processed successfully

#### Scenario: Invalid chart image format
- **WHEN** a chart image is not a valid Base64-encoded PNG
- **THEN** the system SHALL return HTTP 400 with a descriptive error message
