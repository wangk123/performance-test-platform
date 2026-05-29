# Performance Test Platform

Long-term internal performance testing platform built around a Spring Boot backend and Vue 3 frontend.

## Phase 1 Scope

Phase 1 starts the platform foundation:

- Spring Boot backend skeleton.
- Core project and identity domain behavior.
- Vue 3 frontend shell.
- Project management, login, role, and audit concepts prepared for persistence/API work.

## Local Tooling

Target stack:

- Java 17+
- Spring Boot 3.x
- Gradle
- Node.js 20+
- Vue 3 + TypeScript + Vite

Current repository includes dependency-free backend core tests that can run with `javac` while the full Java 17/Spring Boot toolchain is being prepared.

## Backend Core Verification

```bash
mkdir -p /private/tmp/perf-platform-test-classes
javac -d /private/tmp/perf-platform-test-classes backend/src/main/java/com/yr/perftest/platform/identity/*.java backend/src/main/java/com/yr/perftest/platform/project/*.java backend/src/test/java/com/yr/perftest/platform/*.java backend/src/test/java/com/yr/perftest/platform/identity/*.java backend/src/test/java/com/yr/perftest/platform/project/*.java
java -cp /private/tmp/perf-platform-test-classes com.yr.perftest.platform.TestRunner
```

## Planned Full Build

After Java 17 is available:

```bash
gradle :backend:test
cd frontend
npm install
npm run build
```
