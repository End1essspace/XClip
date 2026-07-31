# XClip UI Contract v1.3.0

**Status:** Freeze candidate; becomes frozen after the R11 exit gate passes
**Scope:** Popup, custom window chrome, modal surfaces, Settings styling, keyboard workflow, responsive behavior, and packaged UI resources.

This document is the human-readable counterpart of `/ui/ui-contract-v1.3.0.properties`. Any intentional contract change must update both files and the `UiContractFreezeTest` expectations in the same reviewed milestone.

## 1. Product invariants

- XClip remains a local-first Windows clipboard manager.
- Clipboard commands and copied executable or script paths are never executed automatically.
- Direct Paste restores the captured external window and sends one standard `Ctrl+V`.
- Pinned clips survive normal Clear operations.
- Full clipboard content is never exposed through an automatic row-hover tooltip.
- Large entries use bounded previews.
- Database work and row preparation do not block the JavaFX Application Thread.
- Programmatic JavaFX remains the UI architecture; FXML is not introduced.

## 2. Window contract

- Minimum logical size: `500 × 300`.
- The popup is resizable and supports minimize, maximize/restore, close-to-background, title-bar dragging, and manual edge resizing.
- Restored geometry remains recoverable after monitor removal or DPI/topology changes.
- Negative coordinates are valid for monitors left of or above the primary display.
- Maximized persistence stores the last normal bounds separately.

## 3. Responsive contract

| Mode | Logical width | Required behavior |
|---|---:|---|
| Compact | `≤ 759` | Header, filters, and footer stack; row time may hide; primary actions remain reachable. |
| Balanced | `760–1119` | Single-row shell with reduced row metadata. |
| Wide | `≥ 1120` | Full row metadata and wide shell. |

Row time appears at `700` logical pixels or wider. Layout changes must not cause overlap, unreachable controls, clipped focus rings, or off-screen overlays.

## 4. Popup information architecture

1. Custom title bar.
2. Product/status header.
3. Search and scope/type filters.
4. Virtualized clip list with `PINNED` and `RECENT` sections.
5. Selection/status and action footer.
6. Contextual menus, Quick Help, and modal dialogs.

The canonical scopes are `ALL`, `PINNED`, and `RECENT`. The canonical derived content types are `TEXT`, `CODE`, `URL`, `PATH`, `JSON`, and `COMMAND`.

## 5. Preview and performance budgets

- Expanded preview: at most `18` lines and `8,000` characters.
- Preview cache: at most `4,096` entries.
- Content-type cache: at most `8,192` entries.
- Type-filter candidate scan: at most `5,000` entries.
- Search debounce: `150 ms`.
- Stale asynchronous reload results are discarded.
- Content-type cache keys do not retain full clipboard strings.

## 6. Keyboard contract

The canonical bindings are frozen in `PopupKeyBindings` and serialized into the contract resource. Core workflow:

- `Ctrl+Shift+V` — open XClip and capture target.
- `Ctrl+F` / `Ctrl+K` — focus Search.
- `Ctrl+L` — clear Search.
- `Enter` — Direct Paste.
- `Ctrl+C` — Copy-only outside text input.
- `Ctrl+P` — Pin/Unpin.
- `F2` — rename pinned clip.
- `Alt+Up` / `Alt+Down` — move pinned clip.
- `Delete` — delete selection outside text input.
- `Shift+F10` / Menu key — open Actions.
- `F6` / `Shift+F6` — move between focus zones.
- `Escape` — close the most local active surface before hiding the popup.

Native text editing shortcuts must not be hijacked while Search owns focus.

## 7. Visual and accessibility contract

- Dark theme cascade is deterministic.
- Popup stylesheets: `theme.css`, `controls.css`, `popup.css`.
- Settings/dialog stylesheets: `theme.css`, `controls.css`, `dialogs.css`.
- Lucide SVG resources remain vector-only, packaged, and licensed.
- Focus rings remain visible at 100%, 125%, and 150% Windows scaling.
- Interactive controls expose accessible text/help.
- Section headings are skipped by clip navigation.
- Destructive dialogs keep Cancel as the default action.

## 8. Change-control rule

After R11, visual or interaction changes require all of the following:

1. explicit product reason;
2. updated machine-readable contract when applicable;
3. updated contract test;
4. updated regression matrix if behavior changes;
5. new screenshots;
6. automated and manual R11 gates;
7. separate commit and push.

Tags UI may extend the popup after R11, but it must preserve this frozen baseline unless the contract is deliberately versioned.
