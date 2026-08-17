# XClip R10 — Responsive and Performance Validation

> **Version-line correction — 2026-08-17:** v1.3.0 is the historical release from 2026-06-05. The active development/release target is **v1.4.0**. `app/build.gradle.kts` and the inherited R11 machine-readable resource `ui-contract-v1.3.0.properties` still carry `1.3.0`; synchronizing those artifacts is a required release gate and is not performed by this documentation-only patch.


**Milestone:** R10
**Target version:** 1.4.0
**Platform:** Windows 10/11 x64
**Java:** 17
**JavaFX:** 21

## 1. Automated gate

Run from the repository root:

```powershell
.\gradlew.bat clean test
```

```powershell
.\gradlew.bat build
```

```powershell
git diff --check
```

The R10 automated contract covers:

- deterministic compact, balanced, and wide breakpoints;
- bounded preview and content-type caches;
- stale asynchronous reload rejection;
- off-thread section-row preparation;
- 50,000-entry row fixture;
- 500,000-character fingerprint fixture;
- monitor-bounded Quick Help viewport.

## 2. Resolution matrix

Validate the maximized popup at:

| Resolution | Expected mode |
|---|---|
| 1366×768 | Wide |
| 1920×1080 | Wide |
| 2560×1440 | Wide |
| 3840×2160 | Wide |

Also resize the restored popup through these logical widths:

| Width | Expected behavior |
|---:|---|
| 500–759 px | Compact header, stacked filters/footer, hidden row time |
| 760–1119 px | Balanced single-row shell, reduced row metadata |
| 1120+ px | Full metadata and wide shell |

No control may leave the window, overlap another control, or become unreachable by keyboard.

## 3. Windows scaling matrix

Repeat the visual gate at:

- 100%;
- 125%;
- 150%.

Verify:

- SVG icons remain sharp;
- focus rings are not clipped;
- title-bar controls stay aligned;
- filters and footer do not overflow;
- Quick Help remains inside the current monitor;
- no tooltip displays full clipboard content.

## 4. Data matrix

Use representative databases containing:

- 1,000 clips;
- 10,000 clips;
- 50,000 clips;
- one 500,000-character clip;
- at least 250 pinned clips;
- mixed TEXT, CODE, URL, PATH, JSON, and COMMAND entries.

Expected results:

- opening and searching do not freeze the JavaFX thread;
- scrolling does not mix recycled cell content;
- preview/type caches remain bounded;
- expanded preview stays limited;
- stale rapid-search results never replace a newer query;
- manual pinned order remains stable.

## 5. Stress scenarios

Run each scenario for at least 30 seconds:

1. Type and erase search text rapidly.
2. Switch scope and type filters repeatedly.
3. Scroll from top to bottom and back.
4. Open and hide XClip repeatedly with `Ctrl+Shift+V`.
5. Expand and collapse large previews.
6. Create large Shift-selection ranges.
7. Open and close row/action menus repeatedly.
8. Repeat Direct Paste into Notepad or another safe text target.

Watch for:

- UI stalls;
- layout jumps;
- off-screen menus;
- selection loss;
- stale result flashes;
- steadily increasing memory after the workload stops;
- clipboard content changes not initiated by the user.

## 6. Windows Task Manager memory check

1. Start XClip and record idle private working set.
2. Complete the full stress sequence.
3. Leave XClip idle for two minutes.
4. Record memory again.

A temporary increase is acceptable. Continuous growth during repeated identical cycles is not.

## 7. Completion gate

R10 is complete only when:

- automated tests and build pass;
- every resolution/scaling pair is visually acceptable;
- 50,000-entry history remains usable;
- the 500,000-character clip does not create an unbounded row or tooltip;
- no stale reload result is observed;
- no overlay leaves the active monitor;
- `git diff --check` passes;
- the milestone is committed and pushed.

## 8. 2026-08-17 post-freeze responsive addendum

The following checks are mandatory for the current popup baseline even though the original R10 milestone predates them:

- QHD `2560×1440` at `125%` scaling must be visually reviewed, not inferred from another scale;
- the centered `X-SERIES` wordmark must remain optically centered across restored and maximized layouts;
- the Actions menu must remain readable without becoming oversized;
- Search Assist must open as an overlay without permanently increasing header height;
- in a maximized active window, the exact physical top-right screen corner must highlight/activate Close;
- in a restored window, no destructive close target may exist outside the real window-control area;
- the scrollbar must keep a slim visual thumb while offering a wider logical hit lane;
- in a maximized active window, the physical right edge at the current thumb Y-position must permit normal thumb dragging;
- neighboring monitors and right-side system UI must not become virtual scrollbar/close targets.

These checks are part of the final release regression and do not retroactively alter historical R10 evidence.
