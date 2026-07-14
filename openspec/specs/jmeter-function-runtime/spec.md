# jmeter-function-runtime

## Purpose

jmeter-functions 模块构建与分布式执行时 runtime JAR 注入。

## Requirements

### Requirement: JMeter functions Gradle module

The project SHALL include a `jmeter-functions` Gradle sub-module that builds `perftest-jmeter-functions.jar` containing custom JMeter `AbstractFunction` implementations for Apache JMeter 5.6.3.

#### Scenario: Function class registration

- **WHEN** the `jmeter-functions` module is built
- **THEN** each function class SHALL be registered via `META-INF/services/org.apache.jmeter.functions.Function`
- **AND** the resulting JAR SHALL be copyable to JMeter `lib/ext/`

#### Scenario: JAR bundled in backend runtime

- **WHEN** the backend is built
- **THEN** `perftest-jmeter-functions.jar` SHALL be placed under `backend/src/main/resources/jmeter-runtime/`

### Requirement: Distributed runtime JAR injection

For distributed execution, the system SHALL copy all JAR files from `jmeter-runtime` into the execution working directory and inject them into the JMeter container `lib/ext/` before starting JMeter, using the same mechanism as the existing HdrHistogram JAR.

#### Scenario: All runtime JARs deployed to container

- **WHEN** a distributed execution starts
- **THEN** the platform SHALL include every `jmeter-runtime/*.jar` in execution dependencies uploaded to the remote `/test/` directory
- **AND** the remote runner SHALL copy each JAR to `$JMETER_HOME/lib/ext/` before launching JMeter

#### Scenario: Function available during distributed run

- **WHEN** a script contains `${__funcName(...)}` referencing a platform function
- **AND** distributed execution starts successfully
- **THEN** JMeter SHALL resolve and execute the function during the test run

### Requirement: No platform local execution support

The platform SHALL NOT support running JMeter on the backend host for production test execution. Users requiring local execution SHALL export the JMX script and install `perftest-jmeter-functions.jar` in their local JMeter `lib/ext/`.

#### Scenario: Execution mode is distributed only

- **WHEN** a user submits a scenario for execution through the platform
- **THEN** the execution SHALL use distributed Docker nodes only
- **AND** the platform SHALL NOT invoke `JmeterCommandExecutor` on the backend host for that execution

#### Scenario: Local JMeter usage documented

- **WHEN** a user downloads the function package and exports a JMX script
- **THEN** they SHALL be able to run the script in standalone JMeter after placing the JAR in `lib/ext/` and restarting JMeter

### Requirement: Builtin DATA function set

The `jmeter-functions` module SHALL provide the following DATA category custom functions, each implemented as an `AbstractFunction` and registered via SPI: `randomMobile`, `randomString`, `randomIdCard`, `randomBankCard`, `randomName`, `randomEmail`.

#### Scenario: Random ID card has valid check digit

- **WHEN** `${__randomIdCard()}` is executed
- **THEN** the result SHALL be an 18-character Chinese ID number string
- **AND** the final character SHALL be a valid GB 11643 check digit for the preceding 17 digits

#### Scenario: Random ID card passes full format validation

- **WHEN** `${__randomIdCard()}` is executed
- **THEN** the first 6 digits SHALL be a known mainland county-level administrative division code
- **AND** digits 7–14 SHALL form a valid calendar birth date
- **AND** the full number SHALL pass GB 11643 format validation (area + date + check digit)

#### Scenario: ID card area catalog covers all mainland provinces

- **WHEN** the bundled area-code catalog is loaded
- **THEN** it SHALL include codes for all 31 mainland provincial prefixes (11–65 as applicable)
- **AND** the catalog SHALL contain more than 1000 county-level codes

#### Scenario: Random bank card passes Luhn

- **WHEN** `${__randomBankCard()}` is executed
- **THEN** the result SHALL be a numeric string that passes the Luhn checksum

#### Scenario: Random name and email are non-empty

- **WHEN** `${__randomName()}` or `${__randomEmail()}` is executed
- **THEN** the result SHALL be a non-empty string
- **AND** `${__randomEmail()}` SHALL contain exactly one `@` character

### Requirement: Builtin CODEC function set

The `jmeter-functions` module SHALL provide the following CODEC category custom functions, each implemented as an `AbstractFunction` and registered via SPI: `md5`, `sha256`, `base64Encode`, `base64Decode`, `urlEncode`. Each SHALL accept a single required text parameter treated as UTF-8.

#### Scenario: MD5 and SHA-256 return lowercase hex

- **WHEN** `${__md5(hello)}` or `${__sha256(hello)}` is executed
- **THEN** the result SHALL be the lowercase hexadecimal digest of the UTF-8 bytes of the input

#### Scenario: Base64 round-trip for text

- **WHEN** text is passed to `${__base64Encode(...)}` and the result is passed to `${__base64Decode(...)}`
- **THEN** the decoded value SHALL equal the original UTF-8 text

#### Scenario: URL encode produces percent-encoding

- **WHEN** `${__urlEncode(...)}` is executed with non-ASCII or reserved characters
- **THEN** the result SHALL be UTF-8 percent-encoded per `application/x-www-form-urlencoded` / URLEncoder semantics used by the implementation

### Requirement: Function metadata and SPI stay aligned

Every function key listed in `functions.json` SHALL have a matching SPI-registered `AbstractFunction` whose `getReferenceKey()` equals that key. Unit tests SHALL verify this alignment for the builtin set.

#### Scenario: Registry keys subset of implementations

- **WHEN** the `jmeter-functions` test suite runs
- **THEN** every `key` in `functions.json` SHALL equal some registered function's `getReferenceKey()`
