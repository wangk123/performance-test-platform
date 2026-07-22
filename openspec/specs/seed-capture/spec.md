# seed-capture

## Purpose

可复用采集策略、异步样本与多样本分析。

## Requirements

### Requirement: Capture provider is pluggable
The system SHALL model capture providers so BINLOG can be added later. V1 MUST reject or leave unimplemented any request to run BINLOG capture with a clear unsupported error.

#### Scenario: Binlog not available in V1
- **WHEN** a user requests capture with provider BINLOG
- **THEN** the system returns an unsupported error and does not start recording

### Requirement: Tables without primary keys are marked risky
The system SHALL capture configured tables without primary keys as snapshot data and SHALL mark them as risky. It MUST use order-independent full-row multiset fingerprints for unchanged-table screening, MUST NOT claim reliable UPDATE identity for changed rows, and MUST NOT include such tables in automatically executable clone operations.

#### Scenario: Unchanged no-primary-key table is screened
- **WHEN** a no-primary-key table has the same complete multiset fingerprint in two adjacent samples
- **THEN** the system marks the table unchanged without performing row identity comparison

#### Scenario: Changed no-primary-key table is analyzed
- **WHEN** a no-primary-key table has different fingerprints in adjacent samples
- **THEN** the system reports row-set additions or removals with a risk marker and does not infer reliable UPDATE operations

#### Scenario: No-primary-key operation is excluded from execution
- **WHEN** an analysis result contains a changed no-primary-key table
- **THEN** any generated template marks it risky and excludes it from executable clone operations

### Requirement: Capture strategies are reusable and do not execute on save
The system SHALL allow a project user to create, edit, list, and delete reusable capture strategies containing a datasource, include/exclude filters, worker thread count, and batch row count. Saving a strategy MUST NOT start database capture.

#### Scenario: Save strategy without capture
- **WHEN** a user saves a valid capture strategy
- **THEN** the system persists the configuration and creates no sample record or datasource scan

#### Scenario: Execute strategy repeatedly
- **WHEN** a user manually starts the same strategy multiple times
- **THEN** the system creates one independent sample record for each execution

#### Scenario: Sample preserves executed configuration
- **WHEN** a strategy is edited after a sample has started
- **THEN** the sample retains the immutable strategy configuration and version used at its start

### Requirement: Sample capture is asynchronous and isolated per datasource
The system SHALL execute sample capture asynchronously. It MUST allow at most one active capture per datasource while permitting internal parallelism according to the strategy thread count and batch row count.

#### Scenario: Start asynchronous sample
- **WHEN** a user manually starts a strategy and the datasource has no active capture
- **THEN** the system creates a queued sample immediately and performs database scanning in the background

#### Scenario: Reject concurrent capture on one datasource
- **WHEN** a user starts a strategy whose datasource already has an active sample
- **THEN** the system rejects the new execution and returns the active sample identity

#### Scenario: Parallelize one sample internally
- **WHEN** a sample contains multiple tables or a splittable large primary-key table
- **THEN** the system schedules table or stable primary-key work units up to the configured thread count and reads at most the configured batch row count per batch

### Requirement: Sample execution exposes durable state and progress
The system SHALL persist sample lifecycle state, phase, completed and total table counts, current tables, captured row count, written byte count, active worker count, heartbeat, start time, finish time, and failure details. It MUST support cooperative cancellation.

#### Scenario: Observe long-running capture
- **WHEN** a sample remains active for an extended period
- **THEN** a user can refresh or reopen the page and observe its persisted state and updated table-level progress

#### Scenario: Cancel active capture
- **WHEN** a user cancels an active sample
- **THEN** workers stop after their current batch, uncommitted temporary files are removed, committed chunks are retained, and the sample reaches `CANCELED`

#### Scenario: Capture fails after partial progress
- **WHEN** an error terminates capture after some chunks are committed
- **THEN** the sample reaches `FAILED`, retains its completed data, and is visibly marked incomplete

### Requirement: Samples have stable capture ordering and permanent records
The system SHALL assign each sample an immutable server-side UTC `capture_started_at` and a monotonically increasing `sample_seq` within its strategy. Samples MUST remain listed until manually deleted and MUST be ordered deterministically by capture time and sequence.

#### Scenario: Order samples with distinct times
- **WHEN** a user views samples for a strategy
- **THEN** the system displays their capture start times and orders them by `capture_started_at` and `sample_seq`

#### Scenario: Resolve equal capture times
- **WHEN** two selected samples have equal capture start timestamps
- **THEN** the system uses `sample_seq` as the stable order tie-breaker

#### Scenario: Reopen completed history
- **WHEN** a user returns after a sample succeeds, fails, is canceled, or is interrupted
- **THEN** the sample remains available with status, progress summary, timing, size, and detail entry points

### Requirement: Snapshot data uses committed local file chunks
The system SHALL store capture control metadata and chunk manifests in the platform database and SHALL store snapshot rows as compressed chunks on the configured persistent local disk. Each committed chunk MUST be associated with its sample ID, table, sequence, row count, logical fingerprint, relative path, and physical checksum.

#### Scenario: Commit a snapshot chunk
- **WHEN** a worker finishes writing one batch
- **THEN** it flushes and validates a temporary file, atomically publishes the final file, and only then marks the database manifest ready

#### Scenario: Detect damaged snapshot file
- **WHEN** a chunk file is missing or fails checksum validation
- **THEN** the system marks the affected data incomplete and does not treat it as empty or unchanged

#### Scenario: Protect disk capacity before execution
- **WHEN** available persistent disk space is below the configured low-water threshold
- **THEN** the system rejects a new capture or analysis without deleting historical data

#### Scenario: Protect disk capacity during execution
- **WHEN** an active capture or analysis crosses the configured disk low-water threshold
- **THEN** the system stops the task, records a failure, and retains already committed chunks

### Requirement: Sample details are lazy and server-paginated
The system SHALL provide paginated sample records, table summaries, table schemas, chunk status, and row data. Row detail requests MUST read only the requested chunk range and MUST NOT deserialize an entire table or sample.

#### Scenario: Open sample table summary
- **WHEN** a user opens a sample detail
- **THEN** the system returns database and table summaries without loading all captured rows

#### Scenario: Page through table rows
- **WHEN** a user requests the next row page with a cursor and limit
- **THEN** the system returns only that page plus the next cursor and stable table metadata

### Requirement: Multi-sample analysis is manually selected and adjacent
The system SHALL require a user to select at least three terminal samples from one strategy before starting analysis. It MUST sort selected samples by `capture_started_at ASC, sample_seq ASC` and compute `N-1` adjacent Diffs for `N` samples.

#### Scenario: Reject insufficient samples
- **WHEN** a user selects fewer than three samples
- **THEN** the system rejects analysis creation and explains that at least two adjacent Diff intervals are required

#### Scenario: Build adjacent Diff chain
- **WHEN** a user selects ordered samples `S1`, `S2`, `S3`, and `S4`
- **THEN** the system computes `S2-S1`, `S3-S2`, and `S4-S3` rather than comparing every sample only with `S1`

#### Scenario: Warn about sequence gaps
- **WHEN** selected samples have non-contiguous `sample_seq` values
- **THEN** the system warns that an interval can contain cumulative unselected changes and requires explicit confirmation

#### Scenario: Warn about incompatible strategy snapshots
- **WHEN** selected samples use different strategy versions or incompatible table schemas
- **THEN** the system presents compatibility risks and does not silently treat missing or changed structures as unchanged

### Requirement: Diff uses coarse-to-fine screening
The system SHALL generate stable logical table and chunk fingerprints while capturing data. Analysis MUST compare compatible table manifests first, compare chunk fingerprints only for changed tables, and perform row-level comparison only for changed or unknown chunks.

#### Scenario: Skip unchanged table
- **WHEN** two adjacent complete table snapshots have equal schema hash, row count, and logical content fingerprint
- **THEN** the system marks the table `UNCHANGED` without opening its row chunk files

#### Scenario: Limit fine comparison to changed chunks
- **WHEN** a changed table contains both equal and unequal compatible chunk fingerprints
- **THEN** the system skips equal chunks and performs row-level comparison only for unequal chunks

#### Scenario: Fall back safely when screening is uncertain
- **WHEN** fingerprints are missing, table structures are incompatible, or a sample is incomplete
- **THEN** the system performs a safe fine comparison where possible or reports `UNKNOWN` and does not silently skip the data

#### Scenario: Record screening evidence
- **WHEN** an analysis completes table screening
- **THEN** its permanent result records which tables and chunks were skipped, compared, or marked unknown and why

### Requirement: Incomplete samples can be explicitly analyzed
The system SHALL allow `FAILED`, `CANCELED`, and `INTERRUPTED` samples to participate in analysis only after explicit user confirmation. Missing tables, missing chunks, and failed checksums MUST propagate as `UNKNOWN`.

#### Scenario: Select an incomplete sample
- **WHEN** a user selects an incomplete terminal sample for analysis
- **THEN** the system displays its incomplete coverage and requires confirmation before starting

#### Scenario: Preserve unknown semantics
- **WHEN** an adjacent Diff encounters missing sample data
- **THEN** the result marks that scope `UNKNOWN` and does not infer empty data, no change, INSERT, UPDATE, or DELETE from the absence

### Requirement: Analysis is asynchronous, observable, and permanent
The system SHALL execute analysis asynchronously with durable validation, screening, Diff, inference, persistence, success, failure, cancel, and interruption states. It SHALL expose table-level progress and SHALL permanently retain analysis summaries and paginated detailed results until manual deletion.

#### Scenario: Observe analysis progress
- **WHEN** a multi-sample analysis is active
- **THEN** a user can view its phase, completed and total tables, current table, compared rows, skipped tables, fine-screened chunks, and candidate operation count

#### Scenario: Cancel analysis
- **WHEN** a user cancels active analysis
- **THEN** the system stops cooperatively, retains committed diagnostic output, and records `CANCELED`

#### Scenario: Generate independent template draft
- **WHEN** analysis succeeds
- **THEN** the system persists a self-contained analysis result and template draft and releases all input sample deletion locks

#### Scenario: Delete terminal artifacts independently
- **WHEN** analysis is no longer running
- **THEN** deleting a sample, analysis result, or template does not cascade to either of the other artifact types

### Requirement: Expired running tasks become interrupted
The system SHALL persist task heartbeats and leases. On service startup it MUST identify running capture and analysis records with expired heartbeats, mark them `INTERRUPTED`, release datasource leases and sample locks, and retain committed data without automatically resuming.

#### Scenario: Reconcile capture after restart
- **WHEN** the service starts and finds an expired active capture heartbeat
- **THEN** it marks the sample interrupted, releases the datasource lease, and allows the user to delete and rerun it

#### Scenario: Reconcile analysis after restart
- **WHEN** the service starts and finds an expired active analysis heartbeat
- **THEN** it marks the analysis interrupted, releases its sample locks, and allows the user to delete and restart analysis

### Requirement: Manual deletion is guarded and retryable
The system SHALL prevent deletion of samples locked by active analysis. Terminal samples and analyses MUST be manually deletable through a retryable deleting state that cleans files and manifests without automatic cascading.

#### Scenario: Block deletion during analysis
- **WHEN** a user attempts to delete a sample referenced by active analysis
- **THEN** the system rejects deletion and identifies the active analysis

#### Scenario: Delete terminal sample
- **WHEN** a user deletes an unlocked terminal sample
- **THEN** the system enters a deleting state, removes its local files and manifests, and then removes sample metadata

#### Scenario: Retry failed cleanup
- **WHEN** file or manifest cleanup fails during deletion
- **THEN** the record remains visibly deleting or failed-to-delete and the user can retry cleanup
