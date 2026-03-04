# Sprint 017 Intent: Dashboard Layout Toggle (Card / List)

## Seed

> on the dashboard, users should have the option to switch between a card layout view
> (what we have now) and a more linear horizontal layout like the stages are displayed in,
> but we should still show the same information in both views, there should be a switch on
> the dashboard view to toggle between the different views

## Context

- All JS/CSS lives inside a single Kotlin string in `WebMonitorServer.kt`; no separate
  asset files exist.
- The dashboard is rendered entirely in client-side JS via `renderDashboard()` (~line 2892).
- Card layout: `.dashboard-grid { display: grid; grid-template-columns: repeat(auto-fill,
  minmax(300px, 1fr)); gap: 12px; }`
- Pipeline stage detail view uses a "linear horizontal" pattern: `.stage-list` (vertical
  container of rows) → `.stage-row` (horizontal flex row showing icon + name + elapsed +
  buttons). This is what "layout like the stages" evokes.
- `localStorage` already stores three SPA preferences: `attractor-selected-tab`,
  `attractor-theme`, `attractor-closed-tabs`. The new layout toggle follows the same pattern.

## Recent Sprint Context

- **Sprint 014** — DOT file upload on the Create page; client-side only.
- **Sprint 015** — Comprehensive test coverage sprint; added `WebMonitorServerBrowserApiTest.kt`
  with markup-presence checks.
- **Sprint 016** — Closeable pipeline tabs with `attractor-closed-tabs` localStorage
  persistence. Same client-side pattern we need here.

## Relevant Codebase Areas

| Area | Location | Notes |
|------|----------|-------|
| `renderDashboard()` | ~line 2892 in `WebMonitorServer.kt` | Builds statsHtml + cards HTML |
| `.dashboard-grid` CSS | ~line 2349 | Current card grid styling |
| `.dash-card*` CSS | ~lines 2350–2388 | All card component styles |
| `.stage-list` / `.stage-row` CSS | ~lines 2269–2291 | Inspiration for list layout |
| `localStorage` init block | ~line 2759 | Where new layout init goes |
| `WebMonitorServerBrowserApiTest.kt` | `src/test/kotlin/attractor/web/` | Markup-presence tests |

## Constraints

- Must follow project conventions (no CLAUDE.md; patterns from existing sprints)
- No new Gradle dependencies
- All changes confined to `WebMonitorServer.kt` + its test file
- All `localStorage` calls wrapped in `try/catch` (private-browsing safety)
- Show identical information in both layouts:
  - Pipeline name, status badge, elapsed time, stage progress, stage label,
    stage count, started-at, and action buttons (archive/delete for terminal states)
- Toggle widget must be accessible on the dashboard view toolbar area
- New `localStorage` key: `attractor-dashboard-layout` values: `'card'` | `'list'`

## Success Criteria

1. Dashboard has a visual toggle control (e.g. two icon/label buttons or a segmented
   switch) that flips between Card and List layouts.
2. Card layout: current grid is unchanged.
3. List layout: each pipeline renders as a full-width horizontal row with the same data
   as the card.
4. Selected layout persists across page refreshes via `localStorage`.
5. No information loss between the two views.
6. Markup-presence tests in `WebMonitorServerBrowserApiTest.kt` pass for new symbols.

## Verification Strategy

- Spec/documentation: user's stated requirements above
- Testing approach: markup-presence assertions in `WebMonitorServerBrowserApiTest.kt`
  (same as Sprint 016 strategy)
- Edge cases: empty dashboard (no pipelines), dashboard with closed tabs still shown as
  cards/rows

## Uncertainty Assessment

- Correctness uncertainty: **Low** — self-contained JS/CSS change, no server side
- Scope uncertainty: **Low** — seed is specific; toggle between two views, same data
- Architecture uncertainty: **Low** — follows existing `localStorage` + `renderDashboard()`
  pattern established in Sprint 016

## Open Questions

1. What should the toggle control look like — icon buttons, a segmented control, or a
   text dropdown?  (To be resolved in interview.)
2. Should the list layout also fire the live elapsed-counter timer (`dashboardTimer`) just
   like the card layout?  (Yes — same information requirement means yes.)
3. Should closed pipelines (those with closed tabs) be hidden in the list view the same
   way they'd be on the dashboard?  (Dashboard shows all non-archived, regardless of tab
   state — both views follow same filter logic.)
