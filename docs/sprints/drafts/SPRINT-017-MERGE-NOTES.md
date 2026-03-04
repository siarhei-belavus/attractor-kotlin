# Sprint 017 Merge Notes

## Claude Draft Strengths
- Concrete CSS for both toggle buttons and list rows (`.dash-layout-toggle`, `.dash-lt-btn`,
  `.dashboard-list`, `.dash-list-row`, `.dash-lr-*` family)
- Detailed `buildDashCards()` / `buildDashList()` function signatures with full example code
- Correctly identifies `tickDashboardElapsed()` works unchanged in both layouts via
  `[data-pipeline-id]` attribute
- Empty-state path includes toolbar so toggle is always visible
- 6 markup-presence test assertions mapped to concrete symbols

## Codex Draft Strengths
- Proposes `aria-pressed` on toggle buttons for accessibility
- Explicitly adds responsive/mobile CSS breakpoint as a task
- Introduces shared pipeline data derivation to prevent card/list drift
- Cleaner separation: phase 1 = state, phase 2 = CSS+toggle, phase 3 = renderers,
  phase 4 = timer/edge cases, phase 5 = tests
- Names variable `dashboardLayout` instead of `dashLayout` (longer but self-documenting)

## Valid Critiques Accepted

1. **JSON.stringify(id) in list-row onclick attributes** — the list-row `onclick` and
   action-button attributes should use `JSON.stringify(id)` for the string-embedded IDs
   to guard against pipeline IDs containing quotes. Applied to both `selectTab` and
   action-button handlers in `buildDashList`.

2. **Input validation in `setDashLayout()`** — add `if (mode !== 'card' && mode !== 'list') return;`
   guard at the top of the function before assignment/persist.

3. **Responsive CSS** — add `@media (max-width: 700px)` rules that hide the fixed-width
   columns (progress, stage-label, meta) and let name + status expand. Minimal scope.

4. **Centralize pipeline data derivation** — add a shared helper `dashPipelineData(id)`
   that computes `{status, sc, name, pct, stageLabel, elapsedStr, startedStr, stageCountStr, simBadge, isTerminal}`
   and call it from both `buildDashCards()` and `buildDashList()`. Prevents drift.

5. **Prefer meaningful test markers** — drop the `dashLayout` variable-name assertion in
   favour of `setDashLayout` (already included); keep the five feature-semantic markers:
   `attractor-dashboard-layout`, `setDashLayout`, `dash-layout-toggle`, `dashboard-list`,
   `buildDashList`.

## Critiques Rejected (with reasoning)

- **Rename `dashLayout` → `dashboardLayout`**: The existing codebase uses abbreviated names
  throughout (`closedTabs`, `selectedId`, `panelBuiltFor`). Consistency with existing style
  takes precedence; `dashLayout` is clear enough in context.
- **Replace Gradle command in DoD with `make build`/`make test`**: All prior sprint DoDs
  use the full Gradle command and that is the established convention.

## Interview Refinements Applied

- Toggle buttons are **icon-only** (`⊞` / `≡`) — no text labels — per user's selection.
- Action buttons (Archive/Delete) **are shown** in list rows for terminal pipelines.

## Final Decisions

- Variable: `dashLayout` (abbreviated, consistent with codebase style)
- Functions: `saveDashLayout()`, `setDashLayout(mode)`, `buildDashCards(visibleIds)`,
  `buildDashList(visibleIds, dataFn)` where `dataFn` = `dashPipelineData`
- New helper: `dashPipelineData(id)` returns shared computed values
- New localStorage key: `attractor-dashboard-layout` (values: `'card'` | `'list'`)
- Toggle position: right side of dashboard toolbar row
- Toggle icon-only: `⊞` for cards, `≡` for list (with `title` tooltips "Card view" / "List view")
- `aria-pressed` on toggle buttons (Codex suggestion, no cost, good practice)
- Responsive: hide `.dash-lr-progress`, `.dash-lr-stage-label`, `.dash-lr-meta` columns
  below 700px; name and status badge expand
- Test assertions: 5 markup-presence checks (drop `dashLayout` variable check)
