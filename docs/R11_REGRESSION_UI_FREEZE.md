# XClip R11 — Full Regression and UI Freeze

**Milestone:** R11
**Target:** freeze the v1.3.0 popup/UI baseline before returning to Tags UI
**Platform:** Windows 10/11 x64

## 1. What R11 freezes

R11 freezes the current popup hierarchy, responsive breakpoints, preview/performance budgets, keyboard contract, stylesheet cascade, modal semantics, packaged SVG/resource contract, and Windows lifecycle behavior.

Canonical sources:

- `docs/UI_CONTRACT_v1.3.0.md`;
- `app/src/main/resources/ui/ui-contract-v1.3.0.properties`;
- `docs/R11_REGRESSION_MATRIX.csv`;
- `docs/R11_SCREENSHOT_SET.csv`.

## 2. Start a validation run

Run from the repository root:

```powershell
.\scripts\start_r11_manual_validation.ps1
```

The script creates `artifacts\r11\<timestamp>\` with:

- environment metadata;
- a 38-row results CSV initialized to `PENDING`;
- an empty screenshots folder.

`artifacts\` is local evidence and is intentionally excluded from Git.

## 3. Automated gate

```powershell
.\scripts\run_r11_automated_gate.ps1 -PackageMsi
```

The gate runs:

- clean build and all tests;
- frozen UI contract tests;
- packaged CSS/SVG/license verification;
- R11 documentation/matrix verification;
- `git diff --check`;
- MSI packaging when `-PackageMsi` is supplied;
- MSI SHA-256 capture.

A successful run writes `automated-gate.pass` into the newest R11 evidence directory.

## 4. Manual matrix

Open the generated `R11_REGRESSION_RESULTS.csv`. Execute every row from `R11-001` through `R11-038` and replace `PENDING` with `PASS` only after the expected result is observed.

Do not mark a case PASS based only on code inspection. Enter concise notes and evidence paths for failures, edge cases, or environment-dependent behavior.

Required environments:

- development launch;
- newly built MSI installation;
- 100%, 125%, and 150% Windows scaling;
- compact, balanced, and wide popup widths;
- current multi-monitor topology and at least one restore/topology-change scenario;
- existing schema-v5 database;
- large-history and 500,000-character fixtures from R10.

## 5. Screenshot evidence

Capture every filename listed in `docs/R11_SCREENSHOT_SET.csv` into the generated `screenshots` directory. Do not rename the files because the evidence validator checks exact names.

Screenshots must show the actual current build. Avoid exposing private clipboard content; use synthetic test clips.

## 6. Validate evidence

```powershell
.\scripts\validate_r11_evidence.ps1
```

The validator requires:

- all 38 canonical IDs exactly once;
- every status equal to `PASS`;
- successful automated gate marker;
- MSI package metadata and SHA-256;
- all required screenshots;
- environment metadata.

## 7. Exit gate

R11 is complete only when:

- `r11AutomatedGate` is green;
- Gradle build is green;
- `git diff --check` is green;
- all 38 manual cases are PASS;
- no critical visual regression exists;
- no data-loss behavior is observed;
- multi-monitor validation passes;
- development and MSI launches pass;
- all required screenshots exist;
- evidence validation prints `R11_EVIDENCE_OK`;
- the milestone is committed and pushed;
- working tree is clean.

After this gate, the popup redesign is closed and the roadmap returns to Tags UI.
