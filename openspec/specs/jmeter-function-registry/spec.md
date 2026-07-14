# jmeter-function-registry

## Purpose

平台 JMeter 自定义函数元数据注册表与只读 API。

## Requirements

### Requirement: Function metadata registry

The system SHALL maintain a static function metadata registry (`functions.json`) co-located with the `jmeter-functions` module source code. Each entry SHALL include: `key` (JMeter function reference key without `${}`), `displayName`, `category`, `description`, `parameters` (name, description, required), and `example` (full `${__...}` syntax).

#### Scenario: Registry loaded at startup

- **WHEN** the backend application starts
- **THEN** the system SHALL load function metadata from the bundled `functions.json` resource
- **AND** the loaded registry SHALL be available for API responses without database access

#### Scenario: Registry entry matches implemented function

- **WHEN** a function is listed in `functions.json`
- **THEN** a corresponding `AbstractFunction` implementation SHALL be registered in the JMeter functions JAR with the same reference key

### Requirement: List functions API

The system SHALL expose `GET /api/jmeter-functions` returning all function metadata entries as a JSON array.

#### Scenario: Authenticated user lists functions

- **WHEN** an authenticated user sends `GET /api/jmeter-functions`
- **THEN** the response SHALL return HTTP 200 with an array of function metadata objects
- **AND** each object SHALL include `key`, `displayName`, `category`, `description`, `parameters`, and `example`

#### Scenario: Unauthenticated request rejected

- **WHEN** an unauthenticated client sends `GET /api/jmeter-functions`
- **THEN** the system SHALL reject the request according to platform security rules

### Requirement: Download function package API

The system SHALL expose `GET /api/jmeter-functions/download` to download the bundled `perftest-jmeter-functions.jar` for local JMeter installation.

#### Scenario: Successful download

- **WHEN** an authenticated user sends `GET /api/jmeter-functions/download`
- **THEN** the response SHALL return HTTP 200 with `Content-Type: application/java-archive`
- **AND** the response body SHALL be the current `perftest-jmeter-functions.jar` from `jmeter-runtime`

#### Scenario: Missing JAR

- **WHEN** the function JAR is not present in runtime resources
- **THEN** the system SHALL return an appropriate error response indicating the package is unavailable

### Requirement: No function mutation APIs

The system SHALL NOT provide APIs to create, update, or delete functions via the management console. Function changes SHALL only occur through project code releases.

#### Scenario: No create endpoint

- **WHEN** a client sends `POST /api/jmeter-functions` or `POST /api/projects/{id}/functions`
- **THEN** the system SHALL NOT create functions through management APIs (Mock endpoints SHALL be removed)

### Requirement: Builtin function catalog completeness

The bundled `functions.json` SHALL include metadata entries for the full first-batch builtin set: `randomMobile`, `randomString`, `randomIdCard`, `randomBankCard`, `randomName`, `randomEmail`, `md5`, `sha256`, `base64Encode`, `base64Decode`, `urlEncode`.

#### Scenario: List API returns all first-batch keys

- **WHEN** an authenticated user sends `GET /api/jmeter-functions`
- **THEN** the response array SHALL include an object for each first-batch key listed above
- **AND** each object SHALL include `key`, `displayName`, `category`, `description`, `parameters`, and `example`

### Requirement: DATA and CODEC categories

Each `functions.json` entry SHALL use `category` value `DATA` for data-generation functions or `CODEC` for encode/digest functions.

#### Scenario: Categories assigned correctly

- **WHEN** the registry is loaded
- **THEN** `randomMobile`, `randomString`, `randomIdCard`, `randomBankCard`, `randomName`, and `randomEmail` SHALL have `category` `DATA`
- **AND** `md5`, `sha256`, `base64Encode`, `base64Decode`, and `urlEncode` SHALL have `category` `CODEC`
