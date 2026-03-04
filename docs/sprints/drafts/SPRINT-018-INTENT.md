# Sprint 018 Intent: Pipeline Completion State — Progress Bar & UX Clarity

## Seed

> On the dashboard page, the status bar never turns completely green, which could lead to confusion about whether the pipeline actually completed or not. Is there a better way that we can display this information to the user?

## Context

The dashboard progress bar (`dash-progress-fill`) fills based on `doneStages / totalStages * 100`, where `doneStages` counts stages with `status === 'completed'`. When a pipeline finishes, the **pipeline-level** status transitions to `'completed'` via SSE, but the **last executing stage** may still show status `'running'` in the same payload. The existing fallback `(status === 'completed' ? 100 : 0)` only fires when there are no stages (`totalStages === 0`). When stages are present, the bar stalls short of 100% — commonly at `(N-1)/N * 100` (e.g. 67% for a 3-stage pipeline).

The same mismatch affects `stageCountStr`: a completed 3-stage pipeline may display `2 / 3 stages`, which actively contradicts the "completed" status badge and causes user confusion.

The top colour bar (`dash-card-top`) and badge are both correctly green/completed; only the progress bar and count are wrong.

## Recent Sprint Context

- **Sprint 016** (completed): Closeable pipeline tabs; added `closedTabs` localStorage key and `closeTab()`.
- **Sprint 017** (in_progress): Dashboard layout toggle (card vs. list view); introduced `dashPipelineData()` shared helper consumed by both `buildDashCards()` and `buildDashList()`. The bug exists in `dashPipelineData()` at lines 2931–2933 and 2956. Fixing it here automatically benefits both card and list layouts.

## Relevant Codebase Areas

- `src/main/kotlin/attractor/web/WebMonitorServer.kt`
  - `dashPipelineData(id)` (~line 2921): the single fix point for `pct` and `stageCountStr`
  - `applyUpdate()`: SSE state merge — could detect `completed` transition for optional flash animation
  - CSS block (~line 2390): `.dash-progress-fill` styles; `@keyframes progress-stripe`
- `src/test/kotlin/attractor/web/WebMonitorServerBrowserApiTest.kt`
  - Existing markup-presence assertions; new assertions for any new JS symbols/CSS classes

## Constraints

- No new Gradle dependencies
- All changes confined to `WebMonitorServer.kt` and its test file
- No server-side route changes
- Follow existing JavaScript patterns (var, no ES6 modules)
- Follow existing CSS variable conventions (`var(--border)`, `var(--surface)`, etc.)

## Success Criteria

1. A pipeline card / list row in `completed` state always shows a 100%-wide, solid-green progress bar.
2. The stage count string shows `N / N stages` (not `(N-1) / N stages`) when status is `completed`.
3. Users can immediately tell at a glance that a pipeline fully completed — no ambiguity from a partially-filled bar.
4. (Optional) A brief visual affordance distinguishes "just completed" from "was completed a while ago".
5. All existing tests pass; at least 3 new markup-presence assertions added.

## Verification Strategy

- Reference: Manual inspection of a pipeline that completes with 2+ stages; progress bar must be 100% width, stage count must be `N/N`.
- Unit check: The `pct` override logic is a pure two-line change — correctness is self-evident from code review.
- Tests: Markup-presence assertions in `WebMonitorServerBrowserApiTest.kt` verify the new JS logic and/or CSS class names are present in the served HTML.
- Edge cases:
  - Pipeline with 0 stages completing: existing fallback already returns 100 — no regression.
  - Pipeline with all stages already `completed` when status turns `completed`: pct was already 100 — still 100.
  - Failed / cancelled pipeline: fix must NOT affect pct for those statuses (they should keep actual count).

## Uncertainty Assessment

- **Correctness uncertainty: Low** — root cause is clear (line 2932 fallback only for totalStages==0); fix is two lines.
- **Scope uncertainty: Medium** — user asked "is there a better way?" which may imply they want a UX improvement beyond just the bug fix.
- **Architecture uncertainty: Low** — `dashPipelineData()` is the single touchpoint; both card and list layouts inherit the fix for free.

## Open Questions

1. Should the sprint stop at the two-line bug fix, or also add a visual completion indicator (flash, animation, timestamp)?
2. If adding a "just completed" flash: should it appear on the whole card, just the top bar, or just the progress bar?
3. Should `stageCountStr` show `✓ N / N stages` (with checkmark prefix) for completed pipelines to further reinforce completion?
4. Should `finishedAt` be surfaced more prominently on completed cards (e.g. "Completed 3m ago" instead of just "elapsed: 12s")?
