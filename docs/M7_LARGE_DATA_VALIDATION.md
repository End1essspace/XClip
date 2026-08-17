
# XClip M7.3 Large-Data Validation

> **Version-line status — 2026-08-17:** v1.3.0 is the historical 2026-06-05 release. The active development/release target and Gradle metadata are **v1.4.0**. The current machine-readable UI contract is `ui-contract-v1.4.0.properties` revision 19; the v1.3.0 resource remains frozen only for historical R11 evidence.



> **Current baseline note — 2026-08-17:** this remains the historical M7.3 large-data evidence contract. The current UI polish keeps the same bounded preview, virtualization, database, and memory contracts; a fresh final release gate is still required after all post-freeze UI changes.

Version: v1.4.0
Milestone: M7.3
UI contract revision: 17

## Purpose

M7.3 provides reproducible release evidence for XClip's bounded data and UI
pipeline. It validates the matrix defined by the roadmap without reading or
modifying `%USERPROFILE%\.xclip`.

The harness creates isolated temporary SQLite databases and deletes them after
completion. Machine-readable evidence remains under:

```text
app/build/reports/m7-large-data/
├── summary.json
├── metrics.csv
├── environment.properties
└── PASS.txt or FAIL.txt
```

## Execution model

The normal `test`, `check`, and `build` tasks do not execute the heavy 50,000-row
workload. The matrix is explicit:

```powershell
.\gradlew.bat clean m7LargeDataGate --no-daemon
```

Equivalent repository script:

```powershell
.\scripts\run_m7_large_data_validation.ps1
```

The validation process runs in a dedicated JVM with:

```text
-Xms128m
-Xmx768m
-Xss512k
UTF-8
```

The bounded heap is part of the acceptance contract. A run with an unbounded or
materially larger heap is not canonical M7.3 evidence.

## Deterministic fixture matrix

The harness generates:

- 1,000 clips;
- 10,000 clips;
- 50,000 clips;
- one 500,000-character clip;
- 1,000 PINNED clips with dense manual order;
- 256 tags and deterministic assignments;
- 2,000 equal-content duplicate candidates;
- 25,000 unpinned clips eligible for retention cleanup;
- mixed TEXT, URL, PATH, JSON, COMMAND, and CODE content;
- stable search tokens and timestamps.

Fixture insertion is setup, not a product latency claim. It uses one temporary
transaction and relaxed connection-local synchronization. Every measured
runtime path uses the production SQLite connection configuration and actual
XClip DAO/domain classes.

## Measured production paths

### Startup

`Database.init()` is sampled repeatedly for each dataset size. Evidence records
median and p95 latency. The database is already on the current schema, matching
normal startup after installation or upgrade.

### Popup data preparation

The cold-cache measurement follows the production reload sequence:

1. total clip count;
2. bounded DAO query;
3. advanced-search execution plan;
4. derived content-type filtering;
5. visible tag assignments;
6. tag library;
7. immutable sectioned `PopupRow` preparation.

The list remains bounded to 200 visible clips. The harness separately measures
JavaFX row materialization and records a composite cold-open budget. JavaFX cell
virtualization is not replaced by a 50,000-node UI tree.

### Search and filter latency

The harness measures:

- unique content-token search;
- `tag:` operator search across 256 tags;
- bounded derived type filtering;
- 120 rapid search/filter changes using the production-equivalent pipeline.

### Scroll stability

The same visible result is rebuilt 100 times and must preserve exact clip order.
A JavaFX `ListView` then performs 400 deterministic selection/scroll moves. The
harness records the operation and concurrently samples JavaFX queue delay.

### Retention cleanup

A copy of the 50,000-row fixture is passed through the actual
`HistoryCleanupService`. Exactly 25,000 old RECENT clips must be deleted while
all other rows, including PINNED rows, remain.

### Memory and JavaFX responsiveness

A sampler records peak used heap during the complete matrix. A separate probe
posts work to the JavaFX Application Thread throughout database generation,
queries, row preparation, churn, and cleanup. The run fails when queue latency
exceeds the frozen p95 or maximum-stall budget.

This is direct evidence that heavy work remains off the JavaFX Application
Thread. It does not replace the R10/R11 visual and manual UI validation.

## Frozen hard budgets

| Metric | Budget |
|---|---:|
| 1k startup p95 | 1,500 ms |
| 10k startup p95 | 2,500 ms |
| 50k startup p95 | 5,000 ms |
| cold popup data pipeline p95 | 1,000 ms |
| JavaFX row materialization p95 | 500 ms |
| composite popup-open p95 | 1,500 ms |
| text search p95 | 1,500 ms |
| tag search p95 | 2,000 ms |
| derived type filter p95 | 2,000 ms |
| 2,000-candidate duplicate lookup p95 | 1,500 ms |
| repeated row build p95 | 250 ms |
| 500k clip policy path | 500 ms |
| 25k retention deletion | 20,000 ms |
| 120-query churn total | 45,000 ms |
| main DB size | 512 MiB |
| peak used heap | 700 MiB under `-Xmx768m` |
| JavaFX queue p95 | 250 ms |
| JavaFX maximum stall | 1,000 ms |

Budgets are intentionally conservative release limits, not marketing claims.
The generated report contains actual values for the machine that executed the
gate.

## Failure rules

M7.3 fails when any of the following occurs:

- a fixture count is wrong;
- PINNED order is not dense or deterministic;
- tag or duplicate counts differ from the matrix;
- a popup/search result exceeds the configured UI limit;
- repeated row construction changes ordering;
- retention deletes an incorrect set size;
- a latency, heap, database-size, or JavaFX-stall budget is exceeded;
- JavaFX cannot initialize on the Windows validation host;
- required JSON/CSV/environment evidence is missing.

## Manual review

After a passing gate:

1. open `summary.json` and confirm `status` is `PASS`;
2. inspect `metrics.csv` for the measured values and budgets;
3. retain `environment.properties` with release evidence;
4. run the normal application with a representative large local history;
5. confirm popup opening, search, scrolling, tags, and cleanup remain responsive.

## Gate composition

`m7LargeDataGate` includes:

- the complete `m7DatabaseGate`;
- the full C8/M6/M7.2 regression chain;
- standard and randomized unit/integration suites;
- packaged resource checks;
- frozen UI/database/performance contracts;
- the explicit large-data JVM;
- runtime evidence verification.
