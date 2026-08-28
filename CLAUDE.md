# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Performance test platform built around a Spring Boot backend and Vue 3 frontend. The platform manages JMeter-based performance tests with script versioning, task scheduling, and execution tracking.

## Commands

### Backend (Gradle)

```bash
# Set JAVA_HOME (required)
export JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/

# Run tests
./gradlew :backend:test

# Run single test class
./gradlew :backend:test --tests "com.yr.perftest.platform.script.ScriptServiceTest"

# Start backend server
./gradlew :backend:bootRun
```

### Backend (Dependency-Free Tests)

For quick verification without full Gradle toolchain:

```bash
mkdir -p /private/tmp/perf-platform-test-classes
javac -d /private/tmp/perf-platform-test-classes \
  backend/src/main/java/com/yr/perftest/platform/identity/*.java \
  backend/src/main/java/com/yr/perftest/platform/project/*.java \
  backend/src/test/java/com/yr/perftest/platform/*.java \
  backend/src/test/java/com/yr/perftest/platform/identity/*.java \
  backend/src/test/java/com/yr/perftest/platform/project/*.java
java -cp /private/tmp/perf-platform-test-classes com.yr.perftest.platform.TestRunner
```

### Frontend (Vue 3 + Vite)

```bash
cd frontend
npm install
npm run dev      # Dev server at http://localhost:5173
npm run build    # Type-check and build
npm run preview  # Preview production build
```

The Vite dev server proxies `/api` requests to `http://127.0.0.1:8080`.

## Architecture

### Backend Structure

```
backend/src/main/java/com/yr/perftest/platform/
├── api/            # REST controllers (ScriptController, TaskController, etc.)
├── config/         # Spring configuration (SecurityConfiguration, PlatformServiceConfiguration)
├── execution/      # JMeter execution engine (JmeterCommandExecutor, JmeterResultParser, TestExecutionService)
├── identity/       # Authentication & user management
├── project/        # Project domain and persistence
└── script/         # Script versioning, JMX parsing/rendering (ScriptService, JmeterScriptParser, JmeterScriptRenderer)
```

Key patterns:
- Controllers in `api/` are thin REST endpoints delegating to services
- Services use Spring Data JPA repositories with `Persistent*Record` entities
- Script storage uses filesystem at `${platform.storage.root:./storage}/scripts/{projectId}/`
- JMeter execution via `JmeterCommandExecutor` with result parsing by `JmeterResultParser`

### Frontend Structure

```
frontend/src/
├── api/            # HTTP client (http.ts) and domain APIs (scripts.ts, tasks.ts, etc.)
├── components/     # Reusable components (dialogs, drawers, editor, scripts, tasks, views)
├── composables/    # Vue composition API hooks (useAuth, useScriptEditor, useTaskSchedule, etc.)
├── router/         # Vue Router configuration
├── utils/          # Helpers (jmeter.ts, script-steps.ts, format.ts)
└── views/          # Page-level components (AuthScreen, MainLayout, ScriptEditorPage)
```

Key patterns:
- Uses Element Plus component library
- Composables manage state and side effects
- Script editor supports both JMX upload and step-based visual editing
- API calls use `fetch` wrapper with GET deduplication in `api/http.ts`

### Domain Concepts

- **Project**: Container for scripts, tasks, and executions
- **Script**: JMeter test plan (.jmx) with versioning (ScriptVersion, ScriptDefinition)
- **Task**: Scheduled or manual test execution configuration
- **Execution**: Running task instance with status tracking and results

### Full-suite test note (added 2026-08-28)

Running the entire `:backend:test` suite in ONE Gradle invocation (one test JVM, hundreds of Spring context reloads) eventually trips the Gradle 9 instrumentation agent (`java.lang.instrument ASSERTION FAILED "!errorOutstanding"`) on macOS, causing cascading false failures. Run the suite in per-package chunks (each chunk = fresh JVM), e.g.:

```bash
export JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/
gradle :backend:test --tests "com.yr.perftest.platform.seed.*" --tests "com.yr.perftest.platform.gitlog.*" --tests "com.yr.perftest.platform.auxscript.*"
gradle :backend:test --tests "com.yr.perftest.platform.api.*" --tests "com.yr.perftest.platform.llm.*" --tests "com.yr.perftest.platform.report.*" --tests "com.yr.perftest.platform.monitoring.*" --tests "com.yr.perftest.platform.project.*" --tests "com.yr.perftest.platform.identity.*" --tests "com.yr.perftest.platform.jmeterfunction.*" --tests "com.yr.perftest.platform.script.*" --tests "com.yr.perftest.platform.config.*"
gradle :backend:test --tests "com.yr.perftest.platform.agent.*" --tests "com.yr.perftest.platform.facade.*" --tests "com.yr.perftest.platform.governance.*" --tests "com.yr.perftest.platform.mcp.*" --tests "com.yr.perftest.platform.verification.*" --tests "com.yr.perftest.platform.evidence.*" --tests "com.yr.perftest.platform.analysis.*" --tests "com.yr.perftest.platform.task.*" --tests "com.yr.perftest.platform.execution.*" --tests "com.yr.perftest.platform.BackendCoreBehaviorTest"
```
