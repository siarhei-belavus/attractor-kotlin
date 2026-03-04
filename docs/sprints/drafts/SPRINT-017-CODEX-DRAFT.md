# Sprint 017: Dashboard Layout Toggle (Card / List)

## Overview

The Dashboard currently renders pipelines only as cards in a responsive grid. That works well for scanning small sets, but when users watch many runs they need a denser, linear view that is easier to read row-by-row. The requested behavior is explicit: keep all existing dashboard information, but let users switch between the current card layout and a list layout that feels like the stage rows in the Monitor panel.

This sprint adds a dashboard-level layout toggle with two modes: `card` (current behavior) and `list` (new horizontal rows). The toggle is client-side only, persists via `localStorage`, and defaults safely to `card` when unavailable/corrupt. No server routes change, no data model changes, and no new Gradle dependencies are required.

The implementation stays inside existing UI architecture in `WebMonitorServer.kt` (inline CSS/JS HTML string) and extends the same persistence pattern used by `attractor-selected-tab`, `attractor-theme`, and `attractor-closed-tabs`.

## Use Cases

1. **Dense monitoring mode**: A user with many active/recent runs switches to List view to compare statuses and elapsed times in a compact linear layout.
2. **Visual scanning mode**: A user switches back to Card view for richer per-pipeline visual grouping.
3. **No information loss**: A user confirms both views show pipeline name, status, elapsed, stage progress, stage label, stage counts, started-at metadata, and terminal actions.
4. **Persistent preference**: A user refreshes the page and sees the previously selected dashboard layout restored.
5. **State continuity**: Switching layouts does not interrupt dashboard elapsed ticking, tab selection behavior, archive/delete quick actions, or dashboard sorting.

## Architecture

### Client-side state additions

- New key: `localStorage['attractor-dashboard-layout']`
- Allowed values: `'card' | 'list'`
- In-memory variable: `dashboardLayout`
- Fallback policy: any missing/invalid value resolves to `'card'`

### Rendering strategy

```
renderDashboard()
  compute visibleIds + stats + sorted order (existing behavior)
  render stats block (existing)
  render layout toggle control (new)
  render pipelines via one of:
    - renderDashboardCards(...)  // existing card markup path
    - renderDashboardList(...)   // new list-row markup path
  startDashboardTimer()          // unchanged
```

### Data parity strategy (avoid drift between views)

Use one shared per-pipeline derivation path (status class, elapsed string, stage label, progress %, stage count text, started text, terminal actions eligibility) and consume it in both markup builders. This keeps card/list information equivalent while allowing different layout wrappers.

### List layout model

- Container mirrors stage-list semantics: vertical stack of row items.
- Each row is full-width and horizontally organized:
  - left: status indicator + pipeline name (+ SIM badge)
  - center: status badge, elapsed, stage label, progress bar
  - right: stage count, started-at, terminal action buttons
- Row click still calls `selectTab(id)`.
- Archive/Delete buttons keep `event.stopPropagation()` and existing handlers.

## Implementation Plan

### Phase 1: Add dashboard layout state + persistence (~20%)

**Files:**
- `src/main/kotlin/attractor/web/WebMonitorServer.kt` — Modify

**Tasks:**
- [ ] Add `dashboardLayout` initialization near existing SPA state initialization (`_storedTab`, `closedTabs`):
  - read `attractor-dashboard-layout` in `try/catch`
  - normalize to `'card'` or `'list'`
  - default to `'card'`
- [ ] Add `saveDashboardLayout()` helper with `try/catch` guarded `localStorage.setItem(...)`.
- [ ] Add `setDashboardLayout(layout)` helper:
  - validate layout
  - no-op if unchanged
  - persist
  - rerender dashboard when currently on Dashboard tab

---

### Phase 2: Add toggle UI and styles (~25%)

**Files:**
- `src/main/kotlin/attractor/web/WebMonitorServer.kt` — Modify

**Tasks:**
- [ ] Add CSS for a dashboard toolbar and segmented layout switch (adjacent to existing dashboard CSS):
  - `.dash-toolbar`, `.dash-layout-toggle`, `.dash-layout-btn`, `.dash-layout-btn.active`
- [ ] Add CSS for list layout structures:
  - `.dashboard-list`, `.dash-row`, `.dash-row-main`, `.dash-row-meta`, `.dash-row-progress`, `.dash-row-actions`
- [ ] Add responsive behavior for narrow screens:
  - row content wraps/stack appropriately
  - action buttons remain accessible
- [ ] Inject toggle markup in `renderDashboard()` above cards/list:
  - two buttons: `Card` and `List`
  - `aria-pressed` and `title` attributes for accessibility
  - active styling reflects `dashboardLayout`

---

### Phase 3: Split dashboard rendering into card/list builders (~35%)

**Files:**
- `src/main/kotlin/attractor/web/WebMonitorServer.kt` — Modify

**Tasks:**
- [ ] Refactor `renderDashboard()` to keep current compute/sort logic but branch final pipeline markup on `dashboardLayout`.
- [ ] Keep current card markup as `card` path with no visual regression.
- [ ] Implement list-row markup (`list` path) using same computed data values as card path.
- [ ] Ensure list rows include all required data fields:
  - pipeline name
  - status badge
  - elapsed timer element (`.dash-elapsed[data-pipeline-id]`)
  - progress bar
  - stage label
  - stage count
  - started-at
  - terminal archive/delete quick actions
- [ ] Preserve click semantics:
  - row/cell click opens tab via `selectTab(id)`
  - archive/delete buttons stop propagation and call existing handlers

---

### Phase 4: Timer and empty-state parity checks (~10%)

**Files:**
- `src/main/kotlin/attractor/web/WebMonitorServer.kt` — Modify

**Tasks:**
- [ ] Confirm `tickDashboardElapsed()` continues updating both layouts by keeping `.dash-elapsed[data-pipeline-id]` hook shared.
- [ ] Keep empty dashboard behavior intact in both layouts (stats visible, empty-state message unchanged).
- [ ] Ensure layout toggle still renders when there are zero visible pipelines (if desired UX is toggle-always-visible) or document intentionally hidden behavior.

---

### Phase 5: Regression tests (markup-presence) (~10%)

**Files:**
- `src/test/kotlin/attractor/web/WebMonitorServerBrowserApiTest.kt` — Modify

**Tasks:**
- [ ] Add assertions that `GET /` contains layout toggle markers:
  - `attractor-dashboard-layout`
  - `setDashboardLayout`
  - `dash-layout-toggle`
- [ ] Add assertions that `GET /` contains list layout markers:
  - `dashboard-list`
  - `dash-row`
- [ ] Keep test style consistent with Sprint 016 markup-presence checks.

## Files Summary

| File | Action | Purpose |
|------|--------|---------|
| `src/main/kotlin/attractor/web/WebMonitorServer.kt` | Modify | Add dashboard layout persistence state, toggle UI/CSS, list layout rendering path, and parity-safe shared dashboard data derivation |
| `src/test/kotlin/attractor/web/WebMonitorServerBrowserApiTest.kt` | Modify | Add markup-presence regression assertions for dashboard layout toggle/list layout symbols |

## Definition of Done

### Layout Toggle
- [ ] Dashboard shows a visible Card/List layout toggle in dashboard toolbar area.
- [ ] Toggle supports switching between `card` and `list` without page reload.
- [ ] Toggle has clear active state and accessible semantics (`aria-pressed` or equivalent).

### Card/List Behavior
- [ ] Card layout matches current pre-sprint behavior (no regressions to existing card visuals/interactions).
- [ ] List layout renders full-width horizontal rows inspired by stage-row style.
- [ ] Both layouts expose the same data per pipeline:
  - [ ] Pipeline name
  - [ ] Status badge
  - [ ] Elapsed time
  - [ ] Stage progress bar
  - [ ] Stage label
  - [ ] Stage count
  - [ ] Started-at text
  - [ ] Terminal archive/delete quick actions

### Persistence
- [ ] Selected layout persists in `localStorage['attractor-dashboard-layout']`.
- [ ] On refresh, valid persisted value is restored.
- [ ] Invalid/missing persisted value safely falls back to `card`.
- [ ] All `localStorage` access is wrapped in `try/catch`.

### Interaction Integrity
- [ ] Clicking a card/row still opens the corresponding pipeline tab.
- [ ] Archive/Delete quick actions continue to avoid accidental row selection via `event.stopPropagation()`.
- [ ] Dashboard elapsed timer updates in both layouts.

### Quality
- [ ] Browser API markup-presence tests include new layout markers and pass.
- [ ] No new Gradle dependencies.
- [ ] Scope remains confined to `WebMonitorServer.kt` and `WebMonitorServerBrowserApiTest.kt`.

## Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Card and list views drift in displayed fields over time | Medium | Medium | Centralize shared dashboard data derivation and feed both render paths from same computed values |
| Layout toggle causes noisy rerenders or timer glitches | Low | Medium | Keep `startDashboardTimer()/stopDashboardTimer()` lifecycle unchanged and retain `.dash-elapsed` hooks |
| Row action buttons trigger row click (open tab) | Medium | Low | Reuse existing quick-action handlers with explicit `event.stopPropagation()` |
| Corrupted localStorage value breaks render branch | Low | Medium | Normalize allowed layout values and default to `'card'` |
| Mobile list layout becomes cramped | Medium | Medium | Add responsive wrapping/stacking rules and verify at narrow widths |

## Security Considerations

- Feature is UI-only and stores a non-sensitive preference string in localStorage.
- Continue escaping user-influenced display text with existing `esc(...)` paths.
- Keep inline handler parameters constrained to known layout literals (`'card'`, `'list'`).

## Dependencies

- Sprint 015: `WebMonitorServerBrowserApiTest.kt` exists and is the established place for markup-presence checks.
- Sprint 016: client-side localStorage persistence pattern in dashboard/tab UI (`attractor-closed-tabs`) is available to mirror for layout preference.

## Open Questions

1. Should the toggle be text-only (`Card` / `List`) or include small icons for faster recognition?
2. In list layout, should progress remain a full-width bar in-row, or a compact meter to reduce vertical space?
3. Should the dashboard remember separate scroll position per layout mode, or accept reset-on-toggle behavior for v1?
