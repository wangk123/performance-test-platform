## 1. Persistence Model and Legacy Cutover

- [x] 1.1 Add failing repository tests for strategy versioning, per-strategy sample sequencing, datasource active-capture uniqueness, analysis input locks, and terminal-state queries.
- [x] 1.2 Add capture strategy, sample, sample-table, chunk-manifest, analysis, analysis-input-lock, and analysis-result persistence models with repositories and required indexes.
- [x] 1.3 Add an idempotent legacy cleanup step that removes old `seed_capture_session` data and prevents the retired session flow from being reused.
- [x] 1.4 Update `docs/database/mysql-schema.sql` with the new control-plane tables, constraints, status fields, timestamps, progress counters, fingerprints, and relative paths.

## 2. Chunk Storage and Fingerprints

- [x] 2.1 Add failing tests for canonical row encoding across NULL, text, numeric, temporal, binary, and composite-primary-key values.
- [x] 2.2 Implement canonical row encoding plus logical row, chunk, table, and schema fingerprints, keeping logical fingerprints separate from physical file checksums.
- [x] 2.3 Add failing tests for temporary-file cleanup, atomic publish, checksum validation, bounded streaming reads, and path containment.
- [x] 2.4 Implement the sample/analysis storage layout, JSONL.GZIP streaming writer and reader, temporary-file commit protocol, relative-path manifests, and checksum verification.
- [x] 2.5 Add disk low-water checks before and during capture/analysis, with tests proving that historical files are never auto-deleted.

## 3. Reusable Capture Strategy API

- [x] 3.1 Add failing service and controller tests for strategy create, update, list, detail, and delete validation.
- [x] 3.2 Implement strategy CRUD with datasource ownership checks, required include filters, thread/batch bounds, and configuration version increments.
- [x] 3.3 Add manual execute API behavior that creates a queued sample with an immutable strategy snapshot, UTC `capture_started_at`, and atomic `sample_seq` assignment without scanning synchronously.

## 4. Asynchronous Sample Capture

- [x] 4.1 Add failing tests for datasource lease acquisition, concurrent-start rejection, lease release, and active-sample discovery.
- [x] 4.2 Implement persisted datasource leases, sample heartbeat updates, and the capture state machine from queue through success, failure, cancel, and interruption.
- [x] 4.3 Add failing partition-planner tests for table-level work, single numeric primary-key ranges, composite/string keyset batching, and no-primary-key fallback.
- [x] 4.4 Implement the bounded Worker Pool, table/primary-key work scheduling, batch-row enforcement, JDBC streaming, and no-primary-key risk capture.
- [x] 4.5 Persist table-level progress, current tables, captured rows, written bytes, active workers, committed chunks, completion summaries, and failure details.
- [x] 4.6 Implement cooperative sample cancellation that finishes the current batch, removes uncommitted temporary files, retains READY chunks, and releases the datasource lease.
- [x] 4.7 Implement startup reconciliation that marks expired active samples `INTERRUPTED`, releases their leases, and removes orphan temporary files without resuming work.

## 5. Sample History, Details, and Cleanup

- [x] 5.1 Add failing API tests for paginated sample history, deterministic capture-time ordering, status/time filters, and persisted progress after page reload.
- [x] 5.2 Implement sample list/detail and table-summary endpoints without loading row files.
- [x] 5.3 Add failing cursor-pagination tests that cross chunk boundaries while reading only required files.
- [x] 5.4 Implement cursor-based table-row pagination, schema/chunk diagnostics, checksum/incomplete indicators, and stable next-cursor responses.
- [x] 5.5 Implement guarded sample deletion with active-analysis lock checks, `DELETING` state, file/manifest cleanup, retryable failures, and no template/analysis cascade.

## 6. Coarse-to-Fine Adjacent Diff Engine

- [x] 6.1 Add failing tests that order samples by `capture_started_at, sample_seq`, reject fewer than three samples, generate exactly `N-1` adjacent intervals, and warn on sequence gaps or incompatible versions.
- [x] 6.2 Add parity tests proving table/chunk fingerprint screening produces the same INSERT/UPDATE diagnostics as direct full row Diff.
- [x] 6.3 Implement table-manifest coarse screening that skips complete equal tables without opening snapshot files and records screening evidence.
- [x] 6.4 Implement compatible chunk screening and streaming primary-key merge for changed ranges, with bounded memory and DELETE diagnostics excluded from templates.
- [x] 6.5 Implement no-primary-key multiset comparison and strict `UNKNOWN` propagation for missing tables, chunks, checksums, incompatible schemas, and incomplete samples.
- [x] 6.6 Extend inference to aggregate repeated operations and field signals across adjacent Diffs while preserving confidence, rationale, coverage, and risk evidence.

## 7. Asynchronous Analysis and Independent Results

- [x] 7.1 Add failing tests for analysis input validation, explicit incomplete-sample confirmation, sample deletion locks, progress, cancellation, failure, and interruption.
- [x] 7.2 Implement analysis creation and the persisted `VALIDATING → DIFFING → INFERRING → PERSISTING → SUCCEEDED` state machine with heartbeat and cooperative cancellation.
- [x] 7.3 Persist analysis summaries in the database and detailed row-level Diff chunks on local storage with cursor-paginated result APIs.
- [x] 7.4 Generate a self-contained template draft on successful analysis, persist an immutable input manifest, and release input locks in every terminal state.
- [x] 7.5 Implement startup reconciliation for expired analyses and independent, retryable analysis deletion without cascading to samples or templates.

## 8. Capture and Analysis Frontend

- [x] 8.1 Extend `frontend/src/api/seed.ts` with typed strategy, sample, progress, table-row cursor, analysis, result, cancel, and delete contracts.
- [x] 8.2 Replace the immediate-start recording controls with a focused strategy management component whose save action never executes capture.
- [x] 8.3 Add sample history with manual start, persisted live progress, status/time filters, capture timestamps, incomplete markers, cancel, retry guidance, and guarded delete actions.
- [x] 8.4 Add a lazy sample detail view for database/table summaries, schema and risk metadata, chunk diagnostics, and cursor-paginated rows.
- [x] 8.5 Add multi-select analysis creation with the three-sample gate, earliest-to-latest preview, adjacent interval preview, sequence/version/incomplete warnings, and explicit confirmations.
- [x] 8.6 Add analysis history, progress, cancel/delete actions, screening metrics, paginated Diff details, coverage/UNKNOWN risks, and generated-template navigation.
- [x] 8.7 Split recording responsibilities out of `SeedFactoryView.vue` so each component remains below project line limits and uses existing Ant Design Vue patterns.

## 9. API Cutover and Verification

- [x] 9.1 Remove retired start/sample/finish capture endpoints and frontend calls, then verify no application path can create the legacy synchronous session.
- [x] 9.2 Add backend integration coverage for the full strategy → three samples → adjacent analysis → template draft flow, including failed/canceled sample participation.
- [x] 9.3 Add restart and storage-failure integration coverage for leases, heartbeats, `INTERRUPTED`, disk low-water, damaged chunks, deletion retries, and orphan temporary files.
- [x] 9.4 Run large-data verification showing bounded memory, configured concurrency/batch limits, continuous progress, unchanged tables unopened during Diff, and page-local file reads.
- [x] 9.5 Run backend tests with the project Java 17 `JAVA_HOME`, frontend type-check/build checks, and OpenSpec validation; record final evidence before marking the change complete.
