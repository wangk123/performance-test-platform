# http-editor-var-functions

## Purpose

HTTP 请求编辑器内的变量/函数快捷引用、右侧可滚动面板、语法高亮与请求报文预览。

## Requirements

### Requirement: Unified quick-insert for variables and functions

The HTTP request editor SHALL allow users to quickly insert variables and functions into URL, Params, Headers, and Body (form-data and raw) fields via both `${` autocomplete and the right-side panel.

#### Scenario: Insert into URL Headers Params and form body

- **WHEN** a user focuses an HTTP URL, Params, Headers, or form-body field and selects a variable or function from autocomplete or the right-side panel
- **THEN** the editor SHALL insert the corresponding `${...}` or function example syntax at the caret

#### Scenario: Insert into raw body

- **WHEN** a user focuses the raw Body editor and selects a variable or function from autocomplete or the right-side panel
- **THEN** the editor SHALL insert the syntax at the caret in the raw Body content

#### Scenario: Autocomplete includes functions

- **WHEN** a user types `${` in a supported HTTP request field
- **THEN** suggestions SHALL include project/script variables, platform function examples, and JMeter built-in function examples that match the typed prefix

### Requirement: Scrollable variable and function panel

The HTTP editor right-side variable/function panel SHALL scroll its content independently without expanding the overall page height beyond the HTTP editor viewport.

#### Scenario: Long function list scrolls inside panel

- **WHEN** the combined variable and function lists exceed the panel height
- **THEN** the panel content SHALL scroll vertically within the panel
- **AND** the surrounding script editor page SHALL NOT grow solely due to panel list length

### Requirement: Highlight variables and function calls

HTTP request editable fields that show syntax highlighting SHALL highlight both `${variable}` placeholders and `${__functionName(...)}` function-call syntax.

#### Scenario: Highlight variable and function in URL

- **WHEN** a field value contains `${token}` and `${__UUID()}`
- **THEN** both tokens SHALL be visually highlighted

### Requirement: Request message preview

The HTTP request editor SHALL provide an edit-time request message preview showing the assembled method, URL, headers, and body without sending a network request.

#### Scenario: Preview assembled request

- **WHEN** a user opens the request message preview for the current HTTP step
- **THEN** the UI SHALL display the assembled method, URL, headers, and body based on current editor values

#### Scenario: Preview substitutes variables but not functions

- **WHEN** the preview contains `${variable}` placeholders and `${__func(...)}` function syntax
- **THEN** known variables MAY be substituted for preview display
- **AND** function-call syntax SHALL remain literal
- **AND** the UI SHALL NOT claim that functions were executed in the preview
