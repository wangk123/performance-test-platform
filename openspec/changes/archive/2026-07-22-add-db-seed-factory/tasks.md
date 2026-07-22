## 1. Persistence & Module Skeleton

- [x] 1.1 Add seed/datafactory DB tables (datasource, filter, capture session/sample, template/version, clone job, audit)
- [x] 1.2 Create backend `seed` package skeleton (entities, repos, config for credential encryption)
- [x] 1.3 Remove or disable data-template Mock endpoints in `ModuleMockController`

## 2. Datasource & Filter

- [x] 2.1 Implement project datasource CRUD + encrypted credential storage
- [x] 2.2 Implement JDBC connectivity test API
- [x] 2.3 Implement capture filter model (exact + expression, include/exclude, exclude wins, reject empty include)
- [x] 2.4 Implement table-set evaluation against information_schema / show tables
- [x] 2.5 Backend tests for filter evaluation and empty-include rejection

## 3. Capture (SNAPSHOT)

- [x] 3.1 Define `CaptureProvider` SPI; implement SNAPSHOT; reject BINLOG with unsupported error
- [x] 3.2 Implement capture session lifecycle APIs (start / sample-end / finish)
- [x] 3.3 Implement per-table snapshot + INSERT/UPDATE diff (ignore DELETE for executable path)
- [x] 3.4 Flag no-PK tables as risky / non-executable
- [x] 3.5 Backend tests for multi-sample session and no-PK handling

## 4. Inference & Template Confirm

- [x] 4.1 Implement metadata reader (PK, UK, FK)
- [x] 4.2 Implement role inference + HIGH/MEDIUM/LOW rationale rules
- [x] 4.3 Persist draft template with INSERT/UPDATE operations and column roles
- [x] 4.4 Implement confirm API: generator required for UNIQUE_REGEN/FORMATTED_RAND; LOW must be accepted/changed; emit immutable version
- [x] 4.5 Bind generators for common formats (mobile, id-card aligned with function-library semantics) in platform Java
- [x] 4.6 Backend tests for inference samples and confirm gate (block clone / missing generator)

## 5. Clone Execution

- [x] 5.1 Implement CloneJob creation bound to confirmed version + N + failure policy + max-N validation
- [x] 5.2 Implement batch executor: id_map, dependency order, per-batch transaction, continue/stop policies
- [x] 5.3 Persist job progress, per-batch errors, and audit record
- [x] 5.4 Backend tests for FK rewrite across parent/child and failure policies

## 6. Frontend

- [x] 6.1 Replace ProjectDetail 造数工厂 placeholder with seed workspace shell
- [x] 6.2 UI: datasource list/create/test connection
- [x] 6.3 UI: filter editor + capture session controls (multi-sample)
- [x] 6.4 UI: template confirm page (roles, generators, rationale, confirm)
- [x] 6.5 UI: clone job create + status/result view

## 7. Docs & Cleanup

- [x] 7.1 Update `docs/modules/07-test-data-factory.md` to match V1 (录制确认写库；导出/API/权限后置)
- [x] 7.2 Smoke-check end-to-end against a local MySQL test schema (manual or integration test)
