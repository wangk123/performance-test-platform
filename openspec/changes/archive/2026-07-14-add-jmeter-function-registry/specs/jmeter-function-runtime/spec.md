## ADDED Requirements

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
