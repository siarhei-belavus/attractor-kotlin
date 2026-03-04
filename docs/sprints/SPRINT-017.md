# Sprint 017: Dashboard Layout Toggle (Card / List)

## Overview

The dashboard currently renders every pipeline as a card in a CSS grid — a rich visual
that works well for small to medium pipeline counts. As users accumulate dozens of
pipelines, the card grid becomes scroll-heavy and harder to scan quickly. A compact list
view, where each pipeline occupies a single horizontal row (mirroring the stage-row
aesthetic already present in the pipeline detail panel), lets power users survey many
pipelines at a glance without losing any information.

This sprint adds a two-state icon-only toggle — **⊞** (Cards) | **≡** (List) — to the
dashboard toolbar. Both views show identical information: pipeline name, status badge,
elapsed time, stage progress bar, current-stage label, stage count, started-at timestamp,
and action buttons for terminal pipelines. The selected layout is persisted to
`localStorage` under the new key `attractor-dashboard-layout` so it survives page
refreshes. All other dashboard behaviour — SSE updates, the elapsed counter timer,
archived-pipeline filtering, and closed-tab visibility — is unchanged.

The implementation is entirely client-side. No server routes change, no new Gradle
dependencies, and all modifications are confined to `WebMonitorServer.kt` plus its
existing test file.

## Use Cases

1. **Quick status scan**: A user with 20+ pipelines switches to List view to see all
   pipeline names and statuses at a glance without scrolling through a card grid.
2. **Preference persists**: A user who prefers List view refreshes the page and finds the
   dashboard still in List view.
3. **Return to card view**: A user clicks the ⊞ toggle button to go back to the familiar
   grid layout.
4. **Info parity**: A user in List view can still see elapsed time, stage progress, action
   buttons, and the started-at timestamp — exactly as in Card view.
5. **Empty state**: With no pipelines, both views show the same empty-state message and
   the toggle remains accessible.
6. **Mobile / narrow screens**: Fixed-width columns collapse gracefully below 700px; name
   and status badge remain visible.

## Architecture

```
localStorage keys (client-only, no server involvement)
  attractor-dashboard-layout    NEW — 'card' | 'list'  (default: 'card')

JS module-scope
  var dashLayout = 'card';  // initialised from localStorage

Initialization (after closedTabs block, ~line 2804):
  var _storedLayout;
  try { _storedLayout = localStorage.getItem('attractor-dashboard-layout'); } catch(e){}
  var dashLayout = (_storedLayout === 'list') ? 'list' : 'card';

saveDashLayout() — new helper:
  function saveDashLayout() {
    try { localStorage.setItem('attractor-dashboard-layout', dashLayout); } catch(e){}
  }

setDashLayout(mode) — toggle handler:
  function setDashLayout(mode) {
    if (mode !== 'card' && mode !== 'list') return;
    if (dashLayout === mode) return;
    dashLayout = mode;
    saveDashLayout();
    if (selectedId === DASHBOARD_TAB_ID) renderDashboard();
  }

dashPipelineData(id) — shared computed values used by both render paths:
  Returns: { status, sc, name, pct, stageLabel, elapsedStr, startedStr, stageCountStr, simBadge, isTerminal, cardActions }
  Extracted from existing renderDashboard() per-pipeline block; no logic changes.

renderDashboard() (modified):
  · Computes toolbarHtml with icon-only toggle buttons (⊞ / ≡) + aria-pressed
  · Calls dashPipelineData(id) per pipeline in both builders
  · Branches on dashLayout → buildDashCards(visibleIds) or buildDashList(visibleIds)

buildDashCards(visibleIds) — extracted from current renderDashboard() card loop:
  Unchanged card markup; just moved into named function.

buildDashList(visibleIds) — new list-row builder:
  <div class="dash-list-row" onclick="selectTab(JSON.stringify(id))">
    <div class="dash-lr-status-bar s-{status}"></div>
    <span class="badge badge-{status}">{status}</span>
    {simBadge}
    <span class="dash-lr-name">{name}</span>
    <div class="dash-lr-progress"><div class="dash-progress-fill s-{status}" style="width:{pct}%"></div></div>
    <span class="dash-lr-stage-label">{stageLabel}</span>
    <span class="dash-elapsed s-{status}" id="dash-elapsed-{id}" data-pipeline-id="{id}">{elapsedStr}</span>
    <span class="dash-lr-meta">{stageCountStr} · {startedStr}</span>
    {cardActions}
  </div>
  NOTE: All pipeline IDs embedded in onclick attributes use JSON.stringify(id), not esc(id).

New CSS classes:
  .dash-toolbar        { display:flex; align-items:center; justify-content:flex-end; margin-bottom:12px; }
  .dash-layout-toggle  { display:flex; border:1px solid var(--border); border-radius:6px; overflow:hidden; }
  .dash-lt-btn         { background:var(--surface); color:var(--text-muted); border:none;
                         padding:5px 10px; font-size:0.9rem; cursor:pointer; line-height:1; }
  .dash-lt-btn:hover   { background:var(--surface-muted); color:var(--text); }
  .dash-lt-btn.active  { background:#1c2d3e; color:#79c0ff; }
  [data-theme="light"] .dash-lt-btn.active { background:#e1f0f5; color:#006876; }
  .dashboard-list      { display:flex; flex-direction:column; gap:4px; }
  .dash-list-row       { display:flex; align-items:center; gap:10px; padding:9px 14px;
                         background:var(--surface); border:1px solid var(--border);
                         border-radius:6px; cursor:pointer; overflow:hidden; position:relative;
                         transition:border-color 0.12s; }
  .dash-list-row:hover { border-color:#388bfd; }
  .dash-lr-status-bar  { position:absolute; left:0; top:0; bottom:0; width:3px; flex-shrink:0; }
  .dash-lr-name        { flex:1; font-size:0.9rem; font-weight:600; color:var(--text-strong);
                         overflow:hidden; text-overflow:ellipsis; white-space:nowrap; min-width:0; }
  .dash-lr-progress    { width:80px; flex-shrink:0; height:4px; background:var(--border);
                         border-radius:2px; overflow:hidden; }
  .dash-lr-stage-label { width:160px; flex-shrink:0; font-size:0.78rem; color:var(--text-muted);
                         overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
  .dash-lr-meta        { font-size:0.7rem; color:var(--text-faint); white-space:nowrap; flex-shrink:0; }
  @media (max-width:700px) {
    .dash-lr-progress, .dash-lr-stage-label, .dash-lr-meta { display:none; }
  }

tickDashboardElapsed(): unchanged — queries .dash-elapsed[data-pipeline-id], works in both layouts.
```

## Implementation Plan

### Phase 1: CSS — toolbar, toggle, and list-row styles (~15%)

**Files:**
- `src/main/kotlin/attractor/web/WebMonitorServer.kt` — Modify

**Tasks:**
- [ ] Add `.dash-toolbar`, `.dash-layout-toggle`, `.dash-lt-btn`, `.dash-lt-btn:hover`,
  `.dash-lt-btn.active`, and light-theme override adjacent to existing `.dashboard-grid`
  style (~line 2349)
- [ ] Add `.dashboard-list`, `.dash-list-row`, `.dash-list-row:hover`, `.dash-lr-status-bar`,
  `.dash-lr-name`, `.dash-lr-progress`, `.dash-lr-stage-label`, `.dash-lr-meta` CSS in the
  same block
- [ ] Add `@media (max-width:700px)` rule hiding `.dash-lr-progress`, `.dash-lr-stage-label`,
  `.dash-lr-meta` on narrow screens
- [ ] Reuse existing `.dash-elapsed`, `.badge`, `.dash-card-action-btn`,
  `.dash-progress-fill`, `.dash-sim-badge` — no duplication needed

---

### Phase 2: JS state — init, `saveDashLayout()`, `setDashLayout()` (~15%)

**Files:**
- `src/main/kotlin/attractor/web/WebMonitorServer.kt` — Modify

**Tasks:**
- [ ] After the `closedTabs` init block (~line 2804), add `dashLayout` init:
  ```javascript
  var _storedLayout;
  try { _storedLayout = localStorage.getItem('attractor-dashboard-layout'); } catch(e){}
  var dashLayout = (_storedLayout === 'list') ? 'list' : 'card';
  ```
- [ ] Add `saveDashLayout()` helper at the top of the `// ── Dashboard ──` section:
  ```javascript
  function saveDashLayout() {
    try { localStorage.setItem('attractor-dashboard-layout', dashLayout); } catch(e){}
  }
  ```
- [ ] Add `setDashLayout(mode)` immediately after `saveDashLayout()`:
  ```javascript
  function setDashLayout(mode) {
    if (mode !== 'card' && mode !== 'list') return;
    if (dashLayout === mode) return;
    dashLayout = mode;
    saveDashLayout();
    if (selectedId === DASHBOARD_TAB_ID) renderDashboard();
  }
  ```

---

### Phase 3: `dashPipelineData(id)` — shared data helper (~10%)

**Files:**
- `src/main/kotlin/attractor/web/WebMonitorServer.kt` — Modify

**Tasks:**
- [ ] Extract the per-pipeline computation block (status, sc, name, stages progress, stage
  label, elapsed, startedStr, stageCountStr, simBadge, isTerminal, cardActions) from the
  existing `renderDashboard()` card loop into a standalone function:
  ```javascript
  function dashPipelineData(id) {
    var p = pipelines[id];
    var st = p.state || {};
    var status = st.status || 'idle';
    var sc = 's-' + status;
    var name = esc(st.pipeline || p.fileName || 'pipeline');
    // stage progress ...
    // stage label ...
    // elapsed ...
    // started ...
    // actions ...
    return { status, sc, name, pct, stageLabel, elapsedStr, startedStr,
             stageCountStr, simBadge, isTerminal, cardActions };
  }
  ```
- [ ] The computation logic is identical to what already exists — this is a pure refactor
  of the card loop body

---

### Phase 4: `buildDashCards()` and `buildDashList()` + modified `renderDashboard()` (~45%)

**Files:**
- `src/main/kotlin/attractor/web/WebMonitorServer.kt` — Modify

**Tasks:**
- [ ] Create `buildDashCards(visibleIds)` by moving the existing card-building for-loop
  out of `renderDashboard()` into a named function; call `dashPipelineData(id)` per item:
  ```javascript
  function buildDashCards(visibleIds) {
    var cards = '';
    for (var i = 0; i < visibleIds.length; i++) {
      var id = visibleIds[i];
      var d = dashPipelineData(id);
      // ... existing card HTML ...
    }
    return cards;
  }
  ```
- [ ] Create `buildDashList(visibleIds)` using `dashPipelineData(id)` per item:
  ```javascript
  function buildDashList(visibleIds) {
    var html = '';
    for (var i = 0; i < visibleIds.length; i++) {
      var id = visibleIds[i];
      var d = dashPipelineData(id);
      html += '<div class="dash-list-row" onclick="selectTab(' + JSON.stringify(id) + ')">'
        + '<div class="dash-lr-status-bar ' + d.sc + '"></div>'
        + '<span class="badge badge-' + esc(d.status) + '" style="flex-shrink:0;">' + esc(d.status) + '</span>'
        + d.simBadge
        + '<span class="dash-lr-name">' + d.name + '</span>'
        + '<div class="dash-lr-progress"><div class="dash-progress-fill ' + d.sc + '" style="width:' + d.pct + '%"></div></div>'
        + '<span class="dash-lr-stage-label">' + d.stageLabel + '</span>'
        + '<span class="dash-elapsed ' + d.sc + '" id="dash-elapsed-' + id + '" data-pipeline-id="' + id + '">' + d.elapsedStr + '</span>'
        + '<span class="dash-lr-meta">' + d.stageCountStr + (d.stageCountStr && d.startedStr ? ' \u00b7 ' : '') + d.startedStr + '</span>'
        + d.cardActions
        + '</div>';
    }
    return html;
  }
  ```
  **Important**: `JSON.stringify(id)` is used for the `selectTab` inline onclick argument.
  `esc(id)` only HTML-escapes and is NOT safe for JS string contexts.
- [ ] Build `toolbarHtml` at the top of `renderDashboard()` (after sort, before stats):
  ```javascript
  var toolbarHtml = '<div class="dash-toolbar">'
    + '<div class="dash-layout-toggle">'
    +   '<button class="dash-lt-btn' + (dashLayout === 'card' ? ' active' : '') + '" '
    +   'aria-pressed="' + (dashLayout === 'card' ? 'true' : 'false') + '" '
    +   'onclick="setDashLayout(\'card\')" title="Card view">\u229e</button>'
    +   '<button class="dash-lt-btn' + (dashLayout === 'list' ? ' active' : '') + '" '
    +   'aria-pressed="' + (dashLayout === 'list' ? 'true' : 'false') + '" '
    +   'onclick="setDashLayout(\'list\')" title="List view">\u2261</button>'
    + '</div></div>';
  ```
- [ ] Replace the current single `mainEl.innerHTML = ...` assignment in `renderDashboard()`
  with a branch:
  ```javascript
  if (dashLayout === 'list') {
    mainEl.innerHTML = '<div class="dashboard-layout">' + toolbarHtml + statsHtml
      + '<div class="dashboard-list">' + buildDashList(visibleIds) + '</div></div>';
  } else {
    mainEl.innerHTML = '<div class="dashboard-layout">' + toolbarHtml + statsHtml
      + '<div class="dashboard-grid">' + buildDashCards(visibleIds) + '</div></div>';
  }
  startDashboardTimer();
  ```
- [ ] Update the empty-state path to include `toolbarHtml` so the toggle is always visible:
  ```javascript
  mainEl.innerHTML = '<div class="dashboard-layout">' + toolbarHtml + statsHtml
    + '<div class="dash-empty">...</div></div>';
  ```
- [ ] `tickDashboardElapsed()` requires no changes — it queries `.dash-elapsed[data-pipeline-id]`
  which is present in both layouts

---

### Phase 5: Tests (~15%)

**Files:**
- `src/test/kotlin/attractor/web/WebMonitorServerBrowserApiTest.kt` — Modify

**Tasks:**
- [ ] Add markup-presence assertions to the existing `GET /` body checks:
  - [ ] `GET /` body contains `attractor-dashboard-layout` (localStorage key present)
  - [ ] `GET /` body contains `setDashLayout` (toggle handler present)
  - [ ] `GET /` body contains `dash-layout-toggle` (CSS class present)
  - [ ] `GET /` body contains `dashboard-list` (list container CSS class present)
  - [ ] `GET /` body contains `buildDashList` (list builder function present)

## Files Summary

| File | Action | Purpose |
|------|--------|---------|
| `src/main/kotlin/attractor/web/WebMonitorServer.kt` | Modify | Add toolbar/list CSS; `dashLayout` init; `saveDashLayout()`, `setDashLayout()`, `dashPipelineData()`, `buildDashCards()`, `buildDashList()` JS; modify `renderDashboard()` |
| `src/test/kotlin/attractor/web/WebMonitorServerBrowserApiTest.kt` | Modify | 5 markup-presence assertions for layout toggle symbols |

## Definition of Done

### Toggle Widget
- [ ] Dashboard toolbar shows icon-only `⊞` (Cards) and `≡` (List) toggle buttons in the
  top-right corner of the dashboard area
- [ ] Active layout button has distinct visual styling (`.active` class with highlight)
- [ ] Both buttons carry `aria-pressed` attribute reflecting current state
- [ ] Clicking the already-active button is a no-op (no re-render, no localStorage write)
- [ ] Toggle remains visible in the empty-state view (no pipelines)

### Card Layout
- [ ] Card grid layout is visually unchanged from Sprint 016
- [ ] All existing card information (name, status, elapsed, progress, stage label, footer
  meta, action buttons) still appears
- [ ] No regressions to card interactions (click to open tab, archive, delete)

### List Layout
- [ ] Each pipeline renders as a single horizontal row
- [ ] Row contains: colour-coded left edge, status badge, SIM badge (if applicable),
  pipeline name, progress bar, stage label, elapsed, stage count · started-at, action buttons
- [ ] `tickDashboardElapsed()` updates elapsed spans in list layout without modification
- [ ] Action buttons (Archive/Delete) appear for completed/failed/cancelled pipelines in
  list rows, identical to card view
- [ ] Row click opens the pipeline tab (`selectTab(id)`)
- [ ] Action button clicks do NOT trigger row click (`event.stopPropagation()` still works)
- [ ] Progress bar in list row shows correct fill percentage
- [ ] On screens ≤700px, fixed-width columns (progress, stage label, meta) are hidden;
  name and status badge remain visible

### Data Parity
- [ ] `dashPipelineData(id)` helper extracted and called by both `buildDashCards()` and
  `buildDashList()` — single source of truth for derived pipeline display values
- [ ] `JSON.stringify(id)` used (not `esc(id)`) for pipeline IDs embedded in JS string
  contexts within `buildDashList()` onclick attributes

### Persistence
- [ ] Layout choice written to `localStorage['attractor-dashboard-layout']`
- [ ] Layout preference restored after page refresh
- [ ] Unknown/missing/invalid localStorage value defaults to `'card'`
- [ ] All `localStorage` calls wrapped in `try/catch`

### Tests
- [ ] 5 markup-presence assertions added and pass
- [ ] All existing tests pass (zero regressions)
- [ ] No compiler warnings
- [ ] Build passes: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ~/.gradle/wrapper/dists/gradle-8.7-bin/*/gradle-8.7/bin/gradle -p . jar`
- [ ] No new Gradle dependencies

## Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Card and list views drift in displayed fields over time | Medium | Medium | `dashPipelineData(id)` is a single shared computation; both builders consume it |
| `esc(id)` used instead of `JSON.stringify(id)` in list-row onclick — XSS or corrupted handler | Medium | Medium | DoD and phase doc both specify `JSON.stringify`; enforced in code review |
| Duplicate `id="dash-elapsed-{id}"` if both layouts somehow render simultaneously | Low | Low | `renderDashboard()` replaces `mainEl.innerHTML` completely; only one layout exists at a time |
| `tickDashboardElapsed()` misses spans in list layout | Low | Medium | `buildDashList` spec includes `data-pipeline-id` on the elapsed span (same attr as cards) |
| Action button click triggers row navigation in list layout | Medium | Low | Existing `dashCardArchive`/`dashCardDelete` already call `event.stopPropagation()` |
| Long pipeline names overflow list row | Low | Low | `.dash-lr-name` uses `overflow:hidden; text-overflow:ellipsis; white-space:nowrap; flex:1; min-width:0` |
| `localStorage` unavailable (private browsing, quota exceeded) | Low | Low | Init + save both wrapped in `try/catch`; graceful fallback to `'card'` |

## Security Considerations

- Layout preference is a string `'card'` or `'list'` stored locally; never sent to server.
- `setDashLayout(mode)` validates against an allowlist `{'card', 'list'}` before writing.
- `JSON.stringify(id)` used in all new `onclick` attributes that embed pipeline IDs in JS
  string contexts — not `esc()`, which only HTML-escapes and is NOT safe for JS strings.
- No server routes change; no new attack surface.

## Dependencies

- Sprint 016 (completed) — current codebase as integration target
- No external dependencies

## Open Questions

1. Should there be a "reset scroll position" when switching layouts? Proposed: **defer** —
   reset-on-toggle is acceptable for v1; no additional state needed.
2. Should the toggle appear in the archived-pipeline tab view? Proposed: **no** — the
   archived view uses a table layout unrelated to this feature.
