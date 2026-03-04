# Sprint 017: Dashboard Layout Toggle (Card / List)

## Overview

The dashboard currently renders every pipeline as a card in a CSS grid — a rich visual
that works well for small to medium pipeline counts. As users accumulate dozens of
pipelines, the card grid becomes scroll-heavy and harder to scan quickly. A compact list
view, where each pipeline occupies a single horizontal row (mirroring the stage-row
aesthetic already present in the pipeline detail panel), would let power users survey many
pipelines at a glance without losing any information.

This sprint adds a two-state toggle — **Card** | **List** — to the dashboard toolbar.
Both views show identical information: pipeline name, status badge, elapsed time, stage
progress bar, current-stage label, stage count, started-at timestamp, and action buttons
for terminal pipelines. The selected layout is persisted to `localStorage` under the new
key `attractor-dashboard-layout` so it survives page refreshes. All other dashboard
behaviour — SSE updates, the elapsed counter timer, archived-pipeline filtering, and
closed-tab visibility — is unchanged.

The implementation is entirely client-side. No server routes change, no new Gradle
dependencies, and all modifications are in `WebMonitorServer.kt` plus its existing test
file.

## Use Cases

1. **Quick status scan**: A user with 20+ pipelines switches to List view to see all
   pipeline names and statuses at a glance without scrolling through a card grid.
2. **Preference persists**: A user who prefers List view refreshes the page and finds the
   dashboard still in List view.
3. **Return to card view**: A user clicks the Card toggle button to go back to the
   familiar grid layout.
4. **Info parity**: A user in List view can still see elapsed time, stage progress, action
   buttons, and the started-at timestamp — exactly as in Card view.
5. **Empty state**: With no pipelines, both views show the same empty-state message.

## Architecture

```
localStorage key (client-only, no server involvement)
  attractor-dashboard-layout    NEW — 'card' | 'list'  (default: 'card')

JS module-scope
  var dashLayout = 'card';  // initialised from localStorage

Initialization (near the _closedTabsRaw block, ~line 2804):
  var _storedLayout;
  try { _storedLayout = localStorage.getItem('attractor-dashboard-layout'); } catch(e){}
  var dashLayout = (_storedLayout === 'list') ? 'list' : 'card';

saveDashLayout() — new helper:
  function saveDashLayout() {
    try { localStorage.setItem('attractor-dashboard-layout', dashLayout); } catch(e){}
  }

setDashLayout(mode) — toggle handler (called by toolbar buttons):
  function setDashLayout(mode) {
    if (dashLayout === mode) return;
    dashLayout = mode;
    saveDashLayout();
    if (selectedId === DASHBOARD_TAB_ID) renderDashboard();
  }

renderDashboard() (modified):
  · Adds toolbar HTML with two segmented toggle buttons above statsHtml.
  · Branches on dashLayout:
    - 'card'  → existing .dashboard-grid cards (unchanged)
    - 'list'  → new .dashboard-list rows

Dashboard toolbar HTML:
  '<div class="dash-toolbar">'
  + '<div class="dash-layout-toggle">'
  +   '<button class="dash-lt-btn' + (dashLayout==='card'?' active':'') + '" '
  +   'onclick="setDashLayout(\'card\')" title="Card view">'
  +   '\u229e Cards</button>'
  +   '<button class="dash-lt-btn' + (dashLayout==='list'?' active':'') + '" '
  +   'onclick="setDashLayout(\'list\')" title="List view">'
  +   '\u2261 List</button>'
  + '</div></div>'

List row structure (per pipeline):
  <div class="dash-list-row" onclick="selectTab(id)">
    <div class="dash-lr-status-bar s-{status}"></div>   <!-- 3px left-edge colour -->
    <span class="badge badge-{status}">{status}</span>
    <span class="dash-lr-name">{name}</span>
    <div class="dash-lr-progress">
      <div class="dash-progress-fill s-{status}" style="width:{pct}%"></div>
    </div>
    <span class="dash-lr-stage-label">{stageLabel}</span>
    <span class="dash-elapsed s-{status}" id="dash-elapsed-{id}" data-pipeline-id="{id}">{elapsed}</span>
    <span class="dash-lr-meta">{stageCount} · {startedStr}</span>
    {cardActions}  <!-- archive / delete for terminal pipelines -->
  </div>

New CSS classes:
  .dashboard-list     { display: flex; flex-direction: column; gap: 4px; }
  .dash-list-row      { display: flex; align-items: center; gap: 10px; padding: 9px 14px;
                        background: var(--surface); border: 1px solid var(--border);
                        border-radius: 6px; cursor: pointer; overflow: hidden;
                        position: relative; transition: border-color 0.12s; }
  .dash-list-row:hover { border-color: #388bfd; }
  .dash-lr-status-bar { position: absolute; left: 0; top: 0; bottom: 0; width: 3px; flex-shrink: 0; }
  .dash-lr-name       { flex: 1; font-size: 0.9rem; font-weight: 600;
                        color: var(--text-strong); overflow: hidden;
                        text-overflow: ellipsis; white-space: nowrap; min-width: 0; }
  .dash-lr-progress   { width: 80px; flex-shrink: 0; height: 4px; background: var(--border);
                        border-radius: 2px; overflow: hidden; }
  .dash-lr-stage-label { width: 160px; flex-shrink: 0; font-size: 0.78rem;
                         color: var(--text-muted); overflow: hidden;
                         text-overflow: ellipsis; white-space: nowrap; }
  .dash-lr-meta       { font-size: 0.7rem; color: var(--text-faint); white-space: nowrap;
                        flex-shrink: 0; }
  .dash-toolbar       { display: flex; align-items: center; justify-content: flex-end;
                        margin-bottom: 12px; }
  .dash-layout-toggle { display: flex; border: 1px solid var(--border); border-radius: 6px;
                        overflow: hidden; }
  .dash-lt-btn        { background: var(--surface); color: var(--text-muted); border: none;
                        padding: 5px 12px; font-size: 0.78rem; font-weight: 600;
                        cursor: pointer; line-height: 1; }
  .dash-lt-btn:hover  { background: var(--surface-muted); color: var(--text); }
  .dash-lt-btn.active { background: #1c2d3e; color: #79c0ff; }
  [data-theme="light"] .dash-lt-btn.active { background: #e1f0f5; color: #006876; }
```

## Implementation Plan

### Phase 1: CSS — toolbar and list-row styles (~15%)

**Files:**
- `src/main/kotlin/attractor/web/WebMonitorServer.kt` — Modify

**Tasks:**
- [ ] Add `.dash-toolbar` and `.dash-layout-toggle` and `.dash-lt-btn` CSS rules adjacent
  to the existing `.dashboard-grid` style (~line 2349)
- [ ] Add `.dashboard-list`, `.dash-list-row`, `.dash-lr-status-bar`, `.dash-lr-name`,
  `.dash-lr-progress`, `.dash-lr-stage-label`, `.dash-lr-meta` CSS rules in the same block
- [ ] Reuse existing `.dash-elapsed`, `.badge`, `.dash-card-action-btn`, `.dash-progress-fill`
  classes — no duplication needed

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
    if (dashLayout === mode) return;
    dashLayout = mode;
    saveDashLayout();
    if (selectedId === DASHBOARD_TAB_ID) renderDashboard();
  }
  ```

---

### Phase 3: `renderDashboard()` — inject toolbar and branch on layout (~50%)

**Files:**
- `src/main/kotlin/attractor/web/WebMonitorServer.kt` — Modify

**Tasks:**
- [ ] Build `toolbarHtml` local variable at the top of `renderDashboard()` (after the sort):
  ```javascript
  var toolbarHtml = '<div class="dash-toolbar">'
    + '<div class="dash-layout-toggle">'
    +   '<button class="dash-lt-btn' + (dashLayout === 'card' ? ' active' : '') + '" onclick="setDashLayout(\'card\')" title="Card view">\u229e\u2002Cards</button>'
    +   '<button class="dash-lt-btn' + (dashLayout === 'list' ? ' active' : '') + '" onclick="setDashLayout(\'list\')" title="List view">\u2261\u2002List</button>'
    + '</div></div>';
  ```
- [ ] Prepend `toolbarHtml` before `statsHtml` in the empty-state path and the main render path
- [ ] Extract the existing card-building loop into a helper called `buildDashCards(visibleIds)`:
  — returns the `cards` HTML string (the for-loop body is unchanged)
  — the `mainEl.innerHTML` line becomes:
    `mainEl.innerHTML = '<div class="dashboard-layout">' + toolbarHtml + statsHtml + '<div class="dashboard-grid">' + buildDashCards(visibleIds) + '</div></div>';`
- [ ] Add `buildDashList(visibleIds)` function that generates list rows using same
  per-pipeline data already computed in the card loop:
  ```javascript
  function buildDashList(visibleIds) {
    var html = '';
    for (var i = 0; i < visibleIds.length; i++) {
      var id = visibleIds[i];
      var p = pipelines[id];
      var st = p.state || {};
      var status = st.status || 'idle';
      var sc = 's-' + status;
      var name = esc(st.pipeline || p.fileName || 'pipeline');
      // Stage progress
      var stages = st.stages || [];
      var totalStages = stages.length;
      var doneStages = 0;
      for (var j = 0; j < stages.length; j++) { if (stages[j].status === 'completed') doneStages++; }
      var pct = totalStages > 0 ? Math.min(100, Math.round(doneStages / totalStages * 100))
              : (status === 'completed' ? 100 : 0);
      // Stage label
      var stageLabel = '';
      if (status === 'running') {
        for (var j = 0; j < stages.length; j++) {
          if (stages[j].status === 'running') { stageLabel = '\u25b6\ufe0e\u2002' + esc(stages[j].name); break; }
        }
        if (!stageLabel) stageLabel = 'Waiting\u2026';
      } else if (status === 'paused')    { stageLabel = '\u23f8\ufe0e\u2002Paused'; }
        else if (status === 'completed') { stageLabel = '\u2713\u2002Completed'; }
        else if (status === 'failed')    { stageLabel = '\u2717\u2002Failed'; }
        else if (status === 'cancelled') { stageLabel = '\u2014\u2002Cancelled'; }
      // Elapsed
      var elapsedStr = getDashElapsed(st);
      // Footer meta
      var startedStr = '';
      if (st.startedAt) {
        var d = new Date(st.startedAt);
        var today = new Date();
        startedStr = d.toDateString() !== today.toDateString()
          ? d.toLocaleDateString([], {month:'short', day:'numeric'}) + ' ' + d.toLocaleTimeString([], {hour:'2-digit', minute:'2-digit'})
          : d.toLocaleTimeString([], {hour:'2-digit', minute:'2-digit'});
      }
      var stageCountStr = totalStages > 0 ? doneStages + '\u2009/\u2009' + totalStages + ' stages' : '';
      var metaStr = [stageCountStr, startedStr].filter(Boolean).join(' \u00b7 ');
      var simBadge = p.simulate ? '<span class="dash-sim-badge">SIM</span>' : '';
      var isTerminal = (status === 'completed' || status === 'failed' || status === 'cancelled');
      var cardActions = isTerminal
        ? '<div class="dash-card-actions" style="margin-left:4px;">'
          + '<button class="dash-card-action-btn arch" onclick="dashCardArchive(\'' + id + '\',event)" title="Archive">&#8595;</button>'
          + '<button class="dash-card-action-btn del" onclick="dashCardDelete(\'' + id + '\',event)" title="Delete">&#10005;</button>'
          + '</div>'
        : '';
      html += '<div class="dash-list-row" onclick="selectTab(\'' + id + '\')">'
        + '<div class="dash-lr-status-bar ' + sc + '"></div>'
        + '<span class="badge badge-' + esc(status) + '" style="flex-shrink:0;">' + esc(status) + '</span>'
        + simBadge
        + '<span class="dash-lr-name">' + name + '</span>'
        + '<div class="dash-lr-progress"><div class="dash-progress-fill ' + sc + '" style="width:' + pct + '%"></div></div>'
        + '<span class="dash-lr-stage-label">' + stageLabel + '</span>'
        + '<span class="' + 'dash-elapsed ' + sc + '" id="dash-elapsed-' + id + '" data-pipeline-id="' + id + '">' + elapsedStr + '</span>'
        + '<span class="dash-lr-meta">' + metaStr + '</span>'
        + cardActions
        + '</div>';
    }
    return html;
  }
  ```
- [ ] In the main render branch: use `buildDashList(visibleIds)` when `dashLayout === 'list'`:
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
- [ ] `buildDashCards(visibleIds)` simply moves the existing card-building for-loop body;
  callers are unchanged in behaviour
- [ ] The empty state path also includes `toolbarHtml` so the toggle remains visible even
  with no pipelines
- [ ] `tickDashboardElapsed()` already queries `[data-pipeline-id]` spans — unchanged;
  works for both views because the `id` attribute pattern is preserved

---

### Phase 4: Tests (~20%)

**Files:**
- `src/test/kotlin/attractor/web/WebMonitorServerBrowserApiTest.kt` — Modify

**Tasks:**
- [ ] Add markup-presence assertions to the existing `GET /` body checks:
  - [ ] `GET /` body contains `dashLayout` (JS variable present)
  - [ ] `GET /` body contains `setDashLayout` (toggle handler present)
  - [ ] `GET /` body contains `dash-layout-toggle` (CSS class present)
  - [ ] `GET /` body contains `attractor-dashboard-layout` (localStorage key present)
  - [ ] `GET /` body contains `dashboard-list` (list container CSS class present)
  - [ ] `GET /` body contains `buildDashList` (list builder function present)

---

## Files Summary

| File | Action | Purpose |
|------|--------|---------|
| `src/main/kotlin/attractor/web/WebMonitorServer.kt` | Modify | Add toolbar CSS; `dashLayout` init; `saveDashLayout()`, `setDashLayout()`, `buildDashCards()`, `buildDashList()` JS; modify `renderDashboard()` |
| `src/test/kotlin/attractor/web/WebMonitorServerBrowserApiTest.kt` | Modify | 6 markup-presence assertions for layout toggle symbols |

## Definition of Done

### Toggle Widget
- [ ] Dashboard toolbar shows a "Cards / List" segmented toggle
- [ ] Active view button has distinct visual styling (e.g. `.active` class with highlight)
- [ ] Clicking an already-active view button is a no-op (no re-render)

### Card Layout
- [ ] Card grid layout is visually unchanged from Sprint 016
- [ ] All existing card information (name, status, elapsed, progress, stage label,
  footer meta, action buttons) still appears

### List Layout
- [ ] Each pipeline renders as a single horizontal row
- [ ] Row contains: colour-coded left edge, status badge, SIM badge (if applicable),
  pipeline name, progress bar, stage label, elapsed, stage count · started-at, action buttons
- [ ] `tickDashboardElapsed()` updates elapsed spans in list layout without modification

### Persistence
- [ ] Layout choice written to `localStorage['attractor-dashboard-layout']`
- [ ] Layout preference restored after page refresh
- [ ] Unknown/missing localStorage value defaults to `'card'`
- [ ] All `localStorage` calls wrapped in `try/catch`

### Edge Cases
- [ ] Empty dashboard (no pipelines): toolbar still visible in both layouts
- [ ] Dashboard shows `buildDashList` produces output for running, paused, completed,
  failed, cancelled, idle states
- [ ] Action buttons (archive/delete) still work correctly in list layout

### Tests
- [ ] 6 markup-presence assertions added and pass
- [ ] All existing tests pass (zero regressions)
- [ ] No compiler warnings
- [ ] Build passes: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ~/.gradle/wrapper/dists/gradle-8.7-bin/*/gradle-8.7/bin/gradle -p . jar`
- [ ] No new Gradle dependencies

## Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Duplicate `id="dash-elapsed-{id}"` if both layouts somehow rendered simultaneously | Low | Low | Only one layout is rendered at a time; `renderDashboard()` replaces `mainEl.innerHTML` completely |
| `tickDashboardElapsed()` selects no spans in list layout if `data-pipeline-id` attribute missing | Low | Medium | Spec `buildDashList` to include `data-pipeline-id` attribute on the elapsed span (same as card) |
| Action button `onclick` propagates to row `onclick`, double-navigating | Medium | Low | Existing `dashCardArchive` / `dashCardDelete` already call `event.stopPropagation()` — no new wiring needed |
| Long pipeline names overflow row layout | Low | Low | `.dash-lr-name` uses `overflow:hidden; text-overflow:ellipsis; white-space:nowrap` with `flex:1; min-width:0` |
| Toggle appears in pipeline detail view (wrong context) | Low | Low | Toggle is rendered inside `renderDashboard()` which is only called when `selectedId === DASHBOARD_TAB_ID` |
| `localStorage` unavailable | Low | Low | Init + save both wrapped in `try/catch`; defaults to `'card'` |

## Security Considerations

- Layout preference is a string `'card'` or `'list'` stored locally; never sent to server.
- `setDashLayout(mode)` takes an author-supplied literal argument from `onclick` attributes
  hard-coded in Kotlin string — not user-supplied input. No injection risk.
- No server routes change; no new attack surface.

## Dependencies

- Sprint 016 (completed) — current codebase as integration target
- No external dependencies

## Open Questions

1. Should the toggle use icon-only buttons (smaller, cleaner), text labels, or icon+text?
   Proposed: icon+text (`⊞ Cards` / `≡ List`) for clarity.
2. Should the responsive breakpoint hide the toggle on small screens? Proposed: keep
   visible at all sizes; the list layout is actually _more_ useful on narrow screens.
3. Should there be a separate "compact" card size for the list layout, or is one row with
   inline progress sufficient? Proposed: single row — mirrors the stage-row aesthetic.
