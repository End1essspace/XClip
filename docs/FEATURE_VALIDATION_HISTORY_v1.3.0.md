# XClip v1.3.0 — Feature Validation History

**Document role:** compact historical summary of completed feature validation  
**Current application version:** v1.3.0  
**Current SQLite schema:** 6  
**Current config schema:** 5  
**Current UI contract revision:** 11  

This document replaces the individual milestone validation plans for M2.2 through
M5.3. It preserves the validated contracts and the important release-gate
expectations without keeping twelve repetitive checklists in the repository.

This is a historical reference, not the active UI freeze gate. The canonical
current sources are:

- [`UI_CONTRACT_v1.3.0.md`](UI_CONTRACT_v1.3.0.md);
- [`R10_VALIDATION.md`](R10_VALIDATION.md);
- [`R11_REGRESSION_UI_FREEZE.md`](R11_REGRESSION_UI_FREEZE.md);
- `R11_REGRESSION_MATRIX.csv`;
- `R11_SCREENSHOT_SET.csv`;
- the current automated test suite.

---

## 1. Common milestone gate

Every milestone in this history used the same minimum completion gate:

```powershell
.\gradlew.bat clean test --no-daemon
.\gradlew.bat build --no-daemon
git diff --check
```

Completion also required the applicable manual matrix, a separate commit and
push, and a clean working tree.

The individual historical files mostly repeated these commands and generic
regression checks. Those repetitions are intentionally removed here.

---

## 2. Version progression

| Milestone | Main contract change | Config | SQLite | UI contract at milestone |
|---|---|---:|---:|---:|
| M2.2 | Create and assign tags | 1 | 5 | existing |
| M2.3 | Tag chips and filtering | 1 | 5 | 3 |
| M2.4 | Tag management | 1 | 5 | existing |
| M3.1 | Advanced-search parser | 1 | 5 | existing |
| M3.2 | Advanced-search execution | 1 | 5 | existing |
| M3.3 | Search assistance UI | 1 | 5 | 6 |
| M4.1 | Duplicate domain policy | 1 | 5 | existing |
| M4.2 | Duplicate persistence/runtime | 2 | 6 | existing |
| M4.3 | Duplicate Settings UI | 2 | 6 | 7 |
| M5.1 | Foreground application exclusions | 3 | 6 | existing |
| M5.2 | Sensitive-content rules | 4 | 6 | 9 |
| M5.3 | Retention and cleanup | 5 | 6 | 10 |

The current UI contract is revision 11 because the later repository cleanup
synchronized the frozen icon registry from 32 to 30 resources. That cleanup did
not change the feature semantics summarized below.

---

## 3. Tags

### M2.2 — Create and assign tags

Validated contract:

- `Actions → Tags…` and the row context menu open the tag editor;
- single selection exposes assigned and unassigned tags;
- multi-selection uses checked, unchecked, and mixed states;
- mixed state preserves per-clip differences;
- staged names are not persisted on Cancel;
- names are whitespace-normalized and case-insensitive by identity;
- maximum tag-name length is 64 characters;
- entering an existing name resolves the existing tag;
- creation and assignment are committed in one SQLite transaction;
- validation or database failure rolls back the entire edit;
- clipboard content, pin state, pin order, search state, and selection remain unchanged.

Primary automated coverage:

- tag-name normalization and validation;
- case-insensitive duplicate prevention;
- atomic create/assign/remove behavior;
- tri-state multi-selection model;
- rollback and unknown-id handling.

### M2.3 — Tag chips and filtering

Validated contract:

- row models expose immutable tag metadata;
- rows render a bounded three-chip budget plus `+N` overflow;
- overflow exposes remaining tag names without exposing full clipboard content;
- selected-tag filtering composes with scope, type, and text search;
- text search can match assigned tag names;
- batch tag loading avoids one query per virtualized row;
- DAO ordering remains deterministic;
- Reset clears scope, type, and tag filters together.

Primary automated coverage:

- unified query by selected tag id;
- tag-name search;
- combined scope/tag filtering;
- deterministic batch assignment loading;
- chip overflow policy;
- UI contract revision 3.

### M2.4 — Tag management

Validated contract:

- the management surface remains reachable even with empty history;
- tags are listed in case-insensitive deterministic order;
- usage counts reflect current assignments;
- rename preserves assignments and rejects case/spacing collisions;
- blank and over-64-character names are rejected;
- Cancel does not persist a pending rename or deletion;
- deleting a tag removes assignments but never clipboard entries;
- cleanup removes only zero-usage tags;
- deleting the active filter tag recovers the popup to `All tags`;
- changes persist across restart.

Primary regression coverage included assignment, chips, filtering, tag-name
search, Direct Paste, Copy, row Actions, and multi-selection.

---

## 4. Advanced Search

### M3.1 — Parser foundation

Validated syntax:

```text
type:text
type:code
type:url
type:path
type:json
type:command
is:pinned
is:recent
tag:work
tag:"Project Work"
-type:text
-tag:private
```

Validated parser behavior:

- tokenization is deterministic;
- quoted values support spaces;
- `\"` and `\\` are supported inside quoted values;
- operator names and enum values are case-insensitive;
- clauses preserve source order;
- unknown operator syntax remains ordinary text;
- recognized invalid syntax remains text and emits a non-fatal issue;
- an unterminated quote falls back to the complete raw query;
- parser work remains independent from SQLite and JavaFX.

### M3.2 — Query execution

Validated execution behavior:

- toolbar and query constraints use AND semantics;
- repeated positive `type:` terms use OR semantics;
- repeated positive `tag:` terms use AND semantics;
- negative type and tag terms exclude matches;
- tag identity comparison is case-insensitive;
- pure text searches content, pinned titles, and assigned tag names;
- valid operators are excluded from row highlighting;
- contradictory constraints return an empty result without throwing;
- DAO ordering is preserved;
- derived-type scans are bounded;
- stale asynchronous results cannot replace newer results.

### M3.3 — Search UI

Validated surface:

- focused-search syntax hint;
- contextual operator and tag suggestions;
- keyboard suggestion access;
- active operator chips with bounded overflow;
- inline non-blocking diagnostics;
- Quick Help syntax reference;
- highlighting based only on the pure-text query remainder;
- integration with existing scope, type, and tag toolbar filters.

Saved queries were explicitly deferred and are not part of the v1.3.0 product
contract.

---

## 5. Duplicate behavior

### M4.1 — Domain policy

Validated policy dimensions:

- move or preserve a RECENT duplicate position;
- preserve or move a PINNED duplicate to the top;
- normalize or preserve whitespace;
- case-sensitive or case-insensitive matching;
- unlimited or finite duplicate windows;
- exact-content matching.

Safe defaults preserve the original XClip behavior:

```text
RECENT:       MOVE_TO_TOP
PINNED:       PRESERVE_PIN_POSITION
Whitespace:   NORMALIZE
Case:         SENSITIVE
Window:       UNLIMITED
Exact mode:   OFF
```

The domain decision engine remained independent from configuration persistence,
SQLite, and JavaFX.

### M4.2 — Persistence and runtime

Validated runtime contract:

- config schema advanced from 1 to 2;
- SQLite schema advanced from 5 to 6;
- four policy-independent hashes support every equality mode;
- changing duplicate settings does not rewrite history;
- finite windows may intentionally retain equal rows;
- RECENT and PINNED mutation decisions are applied by `ClipService`;
- the watcher forwards exact bounded text so policy decides case and whitespace;
- legacy unique-hash enforcement is removed safely;
- migration preserves the best legacy row and existing pinned order.

Canonical persisted fields:

```text
duplicateRecentPosition
duplicatePinnedPosition
duplicateWhitespaceMode
duplicateCaseSensitivity
duplicateWindowMillis
duplicateExactContentMode
```

### M4.3 — Settings UI

Validated surface:

- dedicated duplicate section with explanatory copy;
- controls for RECENT/PINNED position, whitespace, case, window, and exact mode;
- exact mode disables whitespace and case controls without discarding selections;
- preset and custom windows round-trip through configuration;
- invalid custom values block Apply;
- reset changes staged values and persists only after Apply;
- closing without Apply restores the last saved state;
- runtime changes apply without restart;
- unrelated settings remain unchanged.

---

## 6. Privacy and retention

### M5.1 — Excluded applications

Validated contract:

- one executable name per Settings line;
- paths normalize to lower-case executable basenames;
- missing `.exe` is added;
- duplicates collapse case-insensitively;
- wildcard entries are rejected;
- foreground matches prevent new clipboard persistence;
- blocked clipboard data is not captured later after switching windows;
- removing an exclusion restores capture immediately;
- foreground resolver failure is fail-open;
- existing history is never deleted or rewritten.

### M5.2 — Sensitive-content rules

Validated rules are explicit opt-in and local-only.

Payment-card candidates require:

- 13–19 digits;
- spaces or hyphens as the only internal separators;
- safe boundaries;
- a leading digit from 2 through 6;
- non-repeating digits;
- a valid Luhn checksum.

One-time-code candidates require:

- 4–8 digits;
- no embedding in a longer digit sequence;
- bounded English, Russian, or Uzbek OTP/2FA/verification context.

Validated safety behavior:

- defaults use `CAPTURE`;
- each rule can independently use `SKIP`;
- standalone numeric values are not treated as OTP;
- invalid-Luhn and ordinary invoice values remain capturable;
- Apply updates the runtime gate immediately;
- skipped values do not appear later after a window switch;
- existing history is not scanned, redacted, rewritten, or deleted;
- M5.1 exclusions remain functional.

### M5.3 — Retention and cleanup

Validated contract:

- all age-based cleanup is disabled by default;
- clear-on-exit is disabled by default;
- general RECENT retention supports 1–3650 days;
- type overrides support TEXT, CODE, URL, PATH, JSON, and COMMAND;
- type value `0` disables that override;
- the shortest applicable active age wins;
- “older than N days” is strict, so the exact boundary is preserved;
- PINNED entries never participate;
- cleanup does not rewrite content or pinned order;
- manual, startup, Settings Apply, periodic, and exit triggers are supported;
- status includes trigger, outcome, deleted count, timestamp, and detail;
- batched deletion remains transactional;
- popup state refreshes after persisted rows are removed;
- clear-on-exit removes RECENT while preserving PINNED titles, tags, and order;
- unrelated privacy, duplicate, capture, and startup settings survive reset/apply.

---

## 7. Preserved release assets

The following files remain separate because they are current executable or
frozen release assets rather than milestone-specific historical checklists:

```text
docs/R10_VALIDATION.md
docs/R11_REGRESSION_UI_FREEZE.md
docs/R11_REGRESSION_MATRIX.csv
docs/R11_SCREENSHOT_SET.csv
docs/UI_CONTRACT_v1.3.0.md
scripts/run_r11_automated_gate.ps1
scripts/start_r11_manual_validation.ps1
scripts/validate_r11_evidence.ps1
```

The Gradle build directly verifies the R11 assets, so they must not be merged or
renamed without a deliberate UI-contract change.

---

## 8. Consolidated source set

This document replaces:

```text
M2_2_TAG_ASSIGNMENT_VALIDATION.md
M2_3_TAG_CHIPS_FILTERING_VALIDATION.md
M2_4_TAG_MANAGEMENT_VALIDATION.md
M3_1_SEARCH_PARSER_VALIDATION.md
M3_2_SEARCH_EXECUTION_VALIDATION.md
M3_3_SEARCH_UI_VALIDATION.md
M4_1_DUPLICATE_DOMAIN_POLICY_VALIDATION.md
M4_2_DUPLICATE_PERSISTENCE_VALIDATION.md
M4_3_DUPLICATE_SETTINGS_UI_VALIDATION.md
M5_1_EXCLUDED_APPLICATIONS_VALIDATION.md
M5_2_SENSITIVE_CONTENT_RULES_VALIDATION.md
M5_3_RETENTION_CLEANUP_VALIDATION.md
```

Detailed behavioral proof remains in the automated tests and the frozen R11
regression assets.
