# Sprint 018: Pipeline Completion State Clarity

## Overview

The dashboard progress bar fills based on how many individual stages carry status `'completed'`.
When a pipeline finishes, the pipeline-level status transitions to `'completed'` through SSE, but
the last executing stage can still carry status `'running'` in the same payload. The existing
fallback that forces 100% only fires when there are no stages at all (`totalStages === 0`). For any
pipeline with stages, the bar stalls at `(N-1)/N * 100` — 67% for a three-stage pipeline, 80% for
five — and the stage count reads `2 / 3 stages`, directly contradicting the green "completed" badge
and top colour bar beside it.

This sprint fixes the root cause in `dashPipelineData()`, the shared helper introduced in Sprint
017 that powers both the card grid and the list layout. The fix is two targeted lines: force
`pct = 100` and force `effectiveDone = totalStages` when the pipeline status is `'completed'`.
Both `buildDashCards()` and `buildDashList()` call `dashPipelineData()`, so both layouts are healed
by the same change. The stage count for completed pipelines also gains a `✓` checkmark prefix to
further reinforce the done state without relying on colour alone.

Beyond the bug fix, this sprint adds a lightweight completion-flash animation. When `applyUpdate()`
detects that a pipeline has transitioned from any non-completed status to `'completed'`, it briefly
adds a `dash-card-flash` CSS class to the card element (if visible in the DOM). A short
`@keyframes` sequence — a green glow that fades out in ~900ms — gives users clear, non-intrusive
feedback about which pipeline just finished. The flash is purely cosmetic and is suppressed for
pipelines that arrive already-completed on initial page load (via a `prevStatuses` tracking map).

## Use Cases

1. **Completion bar fills**: A user watches a three-stage pipeline run. When the pipeline
   transitions to `completed`, the progress bar immediately snaps to 100% wide with a solid green
   fill — not stuck at 67%.
2. **Stage count corrected**: The card footer shows `✓ 3 / 3 stages` (not `2 / 3 stages`) when
   the pipeline completes.
3. **Completion flash**: The card briefly glows green, making it obvious which pipeline just
   finished when multiple pipelines are running simultaneously.
4. **No false flash for pre-completed pipelines**: Pipelines that were already `completed` on page
   load do NOT flash; only pipelines that transition during the session trigger the animation.
5. **List view inherits fix**: The list-row progress bar and stage count are also corrected because
   both layouts consume `dashPipelineData()`.
6. **Failed / cancelled unaffected**: A failed pipeline with `2 / 3` stages actually completed
   continues to show `2 / 3 stages` and a non-100% bar — only `completed` gets the override.

## Architecture

```
dashPipelineData(id) — MODIFIED (single fix point)

  pct calculation (lines 2931-2933):
    BEFORE:
      var pct = totalStages > 0 ? Math.min(100, Math.round(doneStages / totalStages * 100))
              : (status === 'completed' ? 100 : 0);
    AFTER:
      var pct = status === 'completed' ? 100
              : (totalStages > 0 ? Math.min(100, Math.round(doneStages / totalStages * 100)) : 0);

  stageCountStr (line 2956):
    BEFORE:
      var stageCountStr = totalStages > 0 ? doneStages + '\u2009/\u2009' + totalStages + ' stages' : '';
    AFTER:
      var effectiveDone = (status === 'completed') ? totalStages : doneStages;
      var completedPrefix = (status === 'completed' && totalStages > 0) ? '\u2713\u2002' : '';
      var stageCountStr = totalStages > 0
        ? completedPrefix + effectiveDone + '\u2009/\u2009' + totalStages + ' stages' : '';

buildDashCards(visibleIds) — MODIFIED
  Add id="dash-card-{id}" to each .dash-card:
    BEFORE: '<div class="dash-card" onclick="selectTab(\'' + id + '\')">'
    AFTER:  '<div class="dash-card" id="dash-card-' + id + '" onclick="selectTab(\'' + id + '\')">'
  NOTE: id attribute uses raw pipeline ID (same pattern as existing id="dash-elapsed-{id}")

prevStatuses — NEW module-scope var
  var prevStatuses = {};   // { pipelineId: lastObservedStatus }
  Tracks last seen status per pipeline to detect completed transitions.

applyUpdate() — MODIFIED
  Per-pipeline loop, before overwriting pipelines[key]:
    var prevSt = prevStatuses[key];   // undefined on first load
  After merging new state:
    var newSt = (pipelines[key].state || {}).status;
    prevStatuses[key] = newSt;
    if (prevSt !== undefined && prevSt !== 'completed' && newSt === 'completed') {
      flashDashCard(key);
    }
  Also prune on pipeline deletion:
    delete prevStatuses[existingKey];  (alongside existing cleanup)

flashDashCard(id) — NEW
  function flashDashCard(id) {
    var el = document.getElementById('dash-card-' + id);
    if (!el) return;
    el.classList.add('dash-card-flash');
    setTimeout(function() { el.classList.remove('dash-card-flash'); }, 900);
  }

New CSS:
  @keyframes dash-card-glow {
    0%   { box-shadow: 0 0 0px #238636; }
    30%  { box-shadow: 0 0 14px 3px #3fb950; }
    100% { box-shadow: 0 0 0px #238636; }
  }
  .dash-card-flash { animation: dash-card-glow 0.9s ease-out forwards; }
  (added adjacent to .dash-card styles)
```

## Implementation Plan

### Phase 1: Bug fix — `pct` and `stageCountStr` in `dashPipelineData()` (~25%)

**Files:**
- `src/main/kotlin/attractor/web/WebMonitorServer.kt` — Modify

**Tasks:**
- [ ] Replace the `pct` calculation to make `status === 'completed'` authoritative first:
  ```javascript
  var pct = status === 'completed' ? 100
          : (totalStages > 0 ? Math.min(100, Math.round(doneStages / totalStages * 100)) : 0);
  ```
- [ ] Replace the `stageCountStr` line to use `effectiveDone` with checkmark prefix:
  ```javascript
  var effectiveDone = (status === 'completed') ? totalStages : doneStages;
  var completedPrefix = (status === 'completed' && totalStages > 0) ? '\u2713\u2002' : '';
  var stageCountStr = totalStages > 0
    ? completedPrefix + effectiveDone + '\u2009/\u2009' + totalStages + ' stages' : '';
  ```
- [ ] Confirm the old fallback branch `(status === 'completed' ? 100 : 0)` is now superseded
  (the new first branch handles both `totalStages > 0` and `totalStages === 0` for completed)

---

### Phase 2: Flash animation — CSS, `flashDashCard()`, and `prevStatuses` (~40%)

**Files:**
- `src/main/kotlin/attractor/web/WebMonitorServer.kt` — Modify

**Tasks:**
- [ ] Add `@keyframes dash-card-glow` and `.dash-card-flash` CSS adjacent to `.dash-card`
  styles (~line 2370):
  ```css
  @keyframes dash-card-glow {
    0%   { box-shadow: 0 0 0px #238636; }
    30%  { box-shadow: 0 0 14px 3px #3fb950; }
    100% { box-shadow: 0 0 0px #238636; }
  }
  .dash-card-flash { animation: dash-card-glow 0.9s ease-out forwards; }
  ```
- [ ] Add `id="dash-card-' + id + '"` attribute to each `.dash-card` div in `buildDashCards()`:
  ```javascript
  // Before:
  cards += '<div class="dash-card" onclick="selectTab(\'' + id + '\')">'
  // After:
  cards += '<div class="dash-card" id="dash-card-' + id + '" onclick="selectTab(\'' + id + '\')">'
  ```
- [ ] Add `var prevStatuses = {};` in the JS module-scope variable block (near `closedTabs` init)
- [ ] Add `flashDashCard(id)` function in the `// ── Dashboard ──` section:
  ```javascript
  function flashDashCard(id) {
    var el = document.getElementById('dash-card-' + id);
    if (!el) return;
    el.classList.add('dash-card-flash');
    setTimeout(function() { el.classList.remove('dash-card-flash'); }, 900);
  }
  ```
- [ ] In `applyUpdate()`, capture and update `prevStatuses` per pipeline, trigger flash on
  `completed` transition:
  ```javascript
  // Before merging new state:
  var prevSt = prevStatuses[key];
  // ... existing merge of pipelines[key] ...
  var newSt = (pipelines[key].state || {}).status;
  prevStatuses[key] = newSt;
  if (prevSt !== undefined && prevSt !== 'completed' && newSt === 'completed') {
    flashDashCard(key);
  }
  ```
- [ ] In the deletion branch of `applyUpdate()`, prune `prevStatuses`:
  ```javascript
  if (!incoming[existingKey]) {
    delete pipelines[existingKey];
    delete prevStatuses[existingKey];    // NEW
    if (closedTabs[existingKey]) { delete closedTabs[existingKey]; saveClosedTabs(); }
    // ... existing selectedId cleanup ...
  }
  ```

---

### Phase 3: Tests (~35%)

**Files:**
- `src/test/kotlin/attractor/web/WebMonitorServerBrowserApiTest.kt` — Modify

**Tasks:**
- [ ] Add markup-presence assertions to the existing `GET /` body checks:
  - [ ] `GET /` body contains `effectiveDone` (stageCountStr fix variable present)
  - [ ] `GET /` body contains `completedPrefix` (checkmark prefix logic present)
  - [ ] `GET /` body contains `dash-card-flash` (CSS class / JS symbol present)
  - [ ] `GET /` body contains `flashDashCard` (JS function present)
  - [ ] `GET /` body contains `prevStatuses` (transition tracking map present)

## Files Summary

| File | Action | Purpose |
|------|--------|---------|
| `src/main/kotlin/attractor/web/WebMonitorServer.kt` | Modify | Fix `pct` + `stageCountStr` in `dashPipelineData()`; add flash CSS + `flashDashCard()`; add `prevStatuses` map; modify `buildDashCards()` for `id` attr; modify `applyUpdate()` for transition detection |
| `src/test/kotlin/attractor/web/WebMonitorServerBrowserApiTest.kt` | Modify | 5 markup-presence assertions for bug fix and flash symbols |

## Definition of Done

### Bug Fix (Primary)
- [ ] A pipeline with `status === 'completed'` always yields `pct === 100` from
  `dashPipelineData()`, regardless of individual stage statuses
- [ ] `stageCountStr` reads `✓ N / N stages` for a completed pipeline (not `(N-1) / N`)
- [ ] `pct === 100` for completed pipelines with 0 stages (superseded by new first branch)
- [ ] Failed and cancelled pipelines are unaffected: `pct` still reflects actual `doneStages`
- [ ] Both card view and list view show correct 100% bar and `✓ N/N` count for completed
  pipelines (both consume `dashPipelineData()`)

### Completion Flash (Enhancement)
- [ ] CSS `@keyframes dash-card-glow` and `.dash-card-flash` defined in the embedded styles
- [ ] `buildDashCards()` sets `id="dash-card-{id}"` on each `.dash-card`
- [ ] `flashDashCard(id)` uses `document.getElementById()` to find the card and add/remove
  `.dash-card-flash` (not a CSS attribute selector)
- [ ] `prevStatuses` map tracks last observed status per pipeline
- [ ] Flash fires when `applyUpdate()` detects transition from any non-`'completed'` status
  (including `'running'`, `'paused'`, `'failed'`) to `'completed'`
- [ ] Flash does NOT fire for pipelines already `'completed'` on initial page load
  (`prevStatuses[key]` is `undefined` on first encounter — condition requires `!== undefined`)
- [ ] Flash silently no-ops if the card is not in the DOM (e.g. user viewing a detail tab)
- [ ] `prevStatuses[key]` is deleted alongside other cleanup when a pipeline is deleted
- [ ] Flash duration ~900ms; class removed after animation to avoid re-triggering

### Tests
- [ ] 5 markup-presence assertions added and pass
- [ ] All existing tests pass (zero regressions)
- [ ] No compiler warnings
- [ ] Build passes: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ~/.gradle/wrapper/dists/gradle-8.7-bin/*/gradle-8.7/bin/gradle -p . jar`
- [ ] No new Gradle dependencies

## Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Flash fires for pre-completed pipelines on page load | Low | Low | `prevStatuses[key] !== undefined` guard prevents this — first encounter sets the map entry without flashing |
| `id="dash-card-{id}"` collides with another element | Low | Low | ID prefix `dash-card-` is unique in the SPA; pipeline IDs are server-generated slugs |
| Two rapid `completed` transitions re-trigger flash | Low | Low | Class add/remove resets the CSS animation naturally |
| Failed/cancelled pipelines incorrectly forced to 100% | Low | High | Override strictly checks `status === 'completed'` only |
| Deletion cleanup misses `prevStatuses` entry | Low | Low | Explicitly added to deletion branch alongside existing `closedTabs` cleanup |
| List-row flash: flash is card-only, not list rows | Low | Low | Intentional: list rows don't have the `id="dash-card-..."` attribute; they silently no-op |

## Security Considerations

- `id="dash-card-{id}"` uses raw pipeline ID (same pattern as existing `id="dash-elapsed-{id}"`).
  Pipeline IDs are server-generated alphanumeric slugs; no user-controlled content enters this path.
- `flashDashCard()` uses `getElementById` — no CSS selector injection risk.
- No server routes change; no new attack surface.

## Dependencies

- Sprint 017 (in_progress) — introduces `dashPipelineData()` as the shared helper; this sprint
  modifies it. Sprint 018 should be implemented after Sprint 017 is complete, or as a combined
  patch applied on top.
- No external dependencies.

## Open Questions

1. Should the flash also appear in list-view rows? Proposed: **defer** — list rows can be addressed
   in a follow-up if desired; card flash provides sufficient feedback for the primary card layout.
2. Should `finishedAt` be surfaced more prominently on completed cards (e.g. "Completed at 2:34 PM"
   in addition to elapsed)? Proposed: **defer** — the elapsed duration and started-at time already
   provide this context; a separate completion timestamp is a UX enhancement for a future sprint.
