# Sprint 018: Pipeline Completion State Clarity

## Overview

The dashboard progress bar fills based on how many individual stages have status `'completed'`.
When a pipeline finishes, the pipeline-level status transitions to `'completed'` through SSE, but
the last executing stage can still carry status `'running'` in the same payload. The existing
fallback that forces 100% only fires when there are no stages at all. For any pipeline with stages,
the bar stalls at `(N-1)/N * 100` — 67% for a three-stage pipeline, 80% for five stages — and
the stage count reads `2 / 3 stages`, directly contradicting the green "completed" badge beside it.

This sprint fixes the root cause in `dashPipelineData()`, the single shared helper introduced in
Sprint 017 that powers both the card grid and the new list layout. The fix is two targeted lines:
force `pct = 100` and force `effectiveDone = totalStages` when the pipeline status is
`'completed'`. Because both `buildDashCards()` and `buildDashList()` call `dashPipelineData()`,
both layouts are healed by the same change.

Beyond the bug fix the sprint adds a lightweight completion-flash animation: when `applyUpdate()`
detects that a pipeline has just transitioned into `'completed'`, it briefly adds a
`dash-card-flash` CSS class to the card element (if it is visible in the DOM). A short
`@keyframes` sequence — a single full-saturation green glow that fades to normal within ~800ms —
gives users clear, non-intrusive feedback that the pipeline actually finished. The flash is purely
cosmetic; it does not affect any persistent state, localStorage, or SSE logic.

## Use Cases

1. **Completion bar fills**: A user watches a three-stage pipeline run. When the final stage
   finishes and the pipeline transitions to `completed`, the progress bar immediately jumps to
   100% wide with a solid green fill.
2. **Stage count corrected**: The footer shows `3 / 3 stages` (not `2 / 3 stages`) when the
   pipeline completes.
3. **Completion flash in card view**: The completed card briefly glows green, making it obvious
   which pipeline just finished when multiple pipelines are running simultaneously.
4. **No false flash for pre-completed pipelines**: Pipelines that were already `completed` on
   page load do NOT flash; only pipelines that transition from a non-completed status during the
   session trigger the animation.
5. **List view inherits fix**: The list-row progress bar and stage-count are also correct because
   both layouts consume `dashPipelineData()`.
6. **Failed / cancelled unaffected**: A failed pipeline with `2 / 3` stages actually completed
   continues to show `2 / 3 stages` and a non-100% bar — only `completed` gets the override.

## Architecture

```
dashPipelineData(id) — MODIFIED (single fix point)
  Line 2931-2933 (pct calculation):
    BEFORE:
      var pct = totalStages > 0 ? Math.min(100, Math.round(doneStages / totalStages * 100))
              : (status === 'completed' ? 100 : 0);
    AFTER:
      var pct = status === 'completed' ? 100
              : (totalStages > 0 ? Math.min(100, Math.round(doneStages / totalStages * 100)) : 0);

  Line 2956 (stageCountStr):
    BEFORE:
      var stageCountStr = totalStages > 0 ? doneStages + '\u2009/\u2009' + totalStages + ' stages' : '';
    AFTER:
      var effectiveDone = (status === 'completed') ? totalStages : doneStages;
      var stageCountStr = totalStages > 0 ? effectiveDone + '\u2009/\u2009' + totalStages + ' stages' : '';

applyUpdate() — MODIFIED (flash trigger)
  After merging incoming state for a pipeline key, if the previous status was not 'completed'
  and the new status is 'completed', call flashDashCard(key).

flashDashCard(id) — NEW
  function flashDashCard(id) {
    var el = document.querySelector('.dash-card[data-id="' + id + '"]');
    if (!el) return;
    el.classList.add('dash-card-flash');
    setTimeout(function() { el.classList.remove('dash-card-flash'); }, 900);
  }
  NOTE: requires adding data-id="{id}" attribute to each .dash-card element in buildDashCards().

New CSS:
  @keyframes dash-card-glow {
    0%   { box-shadow: 0 0 0px #238636; }
    30%  { box-shadow: 0 0 14px 3px #3fb950; }
    100% { box-shadow: 0 0 0px #238636; }
  }
  .dash-card-flash { animation: dash-card-glow 0.9s ease-out forwards; }
```

## Implementation Plan

### Phase 1: Bug fix — pct and stageCountStr in `dashPipelineData()` (~20%)

**Files:**
- `src/main/kotlin/attractor/web/WebMonitorServer.kt` — Modify

**Tasks:**
- [ ] Replace the `pct` calculation at line 2932 with the `status === 'completed'` guard:
  ```javascript
  var pct = status === 'completed' ? 100
          : (totalStages > 0 ? Math.min(100, Math.round(doneStages / totalStages * 100)) : 0);
  ```
- [ ] Replace the `stageCountStr` line at line 2956 to use `effectiveDone`:
  ```javascript
  var effectiveDone = (status === 'completed') ? totalStages : doneStages;
  var stageCountStr = totalStages > 0 ? effectiveDone + '\u2009/\u2009' + totalStages + ' stages' : '';
  ```
- [ ] Verify the existing `(status === 'completed' ? 100 : 0)` fallback for `totalStages === 0`
  is now superseded and remove the dead branch (the new first branch handles it)

---

### Phase 2: Flash animation — CSS and `flashDashCard()` (~30%)

**Files:**
- `src/main/kotlin/attractor/web/WebMonitorServer.kt` — Modify

**Tasks:**
- [ ] Add `@keyframes dash-card-glow` and `.dash-card-flash` CSS adjacent to
  `.dash-card` styles (~line 2370):
  ```css
  @keyframes dash-card-glow {
    0%   { box-shadow: 0 0 0px #238636; }
    30%  { box-shadow: 0 0 14px 3px #3fb950; }
    100% { box-shadow: 0 0 0px #238636; }
  }
  .dash-card-flash { animation: dash-card-glow 0.9s ease-out forwards; }
  ```
- [ ] Add `data-id` attribute to each `.dash-card` div in `buildDashCards()`:
  ```javascript
  // Before:
  cards += '<div class="dash-card" onclick="selectTab(\'' + id + '\')">'
  // After:
  cards += '<div class="dash-card" data-id="' + esc(id) + '" onclick="selectTab(\'' + id + '\')">'
  ```
- [ ] Add `flashDashCard(id)` function in the `// ── Dashboard ──` section:
  ```javascript
  function flashDashCard(id) {
    var el = document.querySelector('.dash-card[data-id="' + id.replace(/"/g, '') + '"]');
    if (!el) return;
    el.classList.add('dash-card-flash');
    setTimeout(function() { el.classList.remove('dash-card-flash'); }, 900);
  }
  ```
  Note: `data-id` is set with `esc(id)` (HTML-escaped) and queried with a CSS attribute
  selector. Pipeline IDs are server-generated alphanumeric slugs with no special characters,
  so attribute selector matching is safe.

---

### Phase 3: `applyUpdate()` — detect completion transition (~20%)

**Files:**
- `src/main/kotlin/attractor/web/WebMonitorServer.kt` — Modify

**Tasks:**
- [ ] Locate `applyUpdate()` and find the per-pipeline state-merge block. Before overwriting
  `pipelines[key]`, capture the previous status:
  ```javascript
  var prevStatus = pipelines[key] ? (pipelines[key].state || {}).status : null;
  ```
- [ ] After merging the new state (`pipelines[key] = ...`), check for completion transition:
  ```javascript
  var newStatus = (pipelines[key].state || {}).status;
  if (prevStatus !== 'completed' && newStatus === 'completed') {
    flashDashCard(key);
  }
  ```
- [ ] The flash call is inside the per-pipeline loop in `applyUpdate()`; it only runs when
  `selectedId === DASHBOARD_TAB_ID` is NOT checked — the flash fires even when viewing a
  pipeline detail tab, so the card will flash if the user switches back to dashboard. Actually,
  because `flashDashCard` queries the DOM, it silently no-ops if the card is not in the DOM;
  this is the correct behaviour. No guard on `selectedId` needed.

---

### Phase 4: Tests (~30%)

**Files:**
- `src/test/kotlin/attractor/web/WebMonitorServerBrowserApiTest.kt` — Modify

**Tasks:**
- [ ] Add markup-presence assertions to the existing `GET /` body checks:
  - [ ] `GET /` body contains `dash-card-flash` (CSS class / JS symbol present)
  - [ ] `GET /` body contains `flashDashCard` (JS function present)
  - [ ] `GET /` body contains `dash-card-glow` (keyframes name present)
  - [ ] `GET /` body contains `effectiveDone` (stageCountStr fix variable present)
  - [ ] `GET /` body contains `status === 'completed' ? 100` (pct override present — or
    a substring unique to the new pct logic, e.g. `completed' ? 100`)

## Files Summary

| File | Action | Purpose |
|------|--------|---------|
| `src/main/kotlin/attractor/web/WebMonitorServer.kt` | Modify | Fix `pct` and `stageCountStr` in `dashPipelineData()`; add flash CSS + `flashDashCard()`; modify `buildDashCards()` to add `data-id`; modify `applyUpdate()` to trigger flash |
| `src/test/kotlin/attractor/web/WebMonitorServerBrowserApiTest.kt` | Modify | 5 markup-presence assertions for new symbols |

## Definition of Done

### Bug Fix
- [ ] A pipeline with `status === 'completed'` always yields `pct === 100` from `dashPipelineData()`, regardless of individual stage statuses
- [ ] `stageCountStr` reads `N / N stages` for a completed pipeline (not `(N-1) / N`)
- [ ] `pct === 100` for completed pipelines with 0 stages (pre-existing: covered by new simplified branch)
- [ ] Failed and cancelled pipelines are unaffected: their `pct` still reflects actual doneStages
- [ ] Both card view and list view show correct 100% bar for completed pipelines (both consume `dashPipelineData()`)

### Flash Animation
- [ ] CSS `@keyframes dash-card-glow` and `.dash-card-flash` class defined in the embedded styles
- [ ] `buildDashCards()` sets `data-id` attribute on each `.dash-card`
- [ ] `flashDashCard(id)` function queries `[data-id]` and adds/removes `.dash-card-flash`
- [ ] Flash fires when `applyUpdate()` detects transition from any non-completed status to `completed`
- [ ] Flash does NOT fire for pipelines that arrive already-completed on initial SSE load
- [ ] Flash silently no-ops if the card is not in the DOM (e.g. user is viewing a detail tab)
- [ ] Flash duration is ~900ms; class removed after animation to avoid re-triggering

### Tests
- [ ] 5 markup-presence assertions added and pass
- [ ] All existing tests pass (zero regressions)
- [ ] No compiler warnings
- [ ] Build passes: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ~/.gradle/wrapper/dists/gradle-8.7-bin/*/gradle-8.7/bin/gradle -p . jar`
- [ ] No new Gradle dependencies

## Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Flash fires on page load for pre-completed pipelines | Medium | Low | `applyUpdate()` only triggers flash when `prevStatus !== 'completed'`; on initial load `prevStatus` is `null`, which is `!== 'completed'` — need to guard the very first `applyUpdate()` call |
| `applyUpdate()` initial-load flash for pre-existing completed pipelines | Medium | Low | Introduce an `initialLoadDone` flag set after the first full SSE batch; flash only fires if `initialLoadDone` is true |
| `data-id` attribute introduces XSS if pipeline IDs contain HTML special chars | Low | Low | IDs are server-generated slugs (alphanumeric + hyphens); `esc(id)` ensures any edge case is HTML-encoded |
| Flash animation conflicts with `dash-card-flash` if two completions arrive fast | Low | Low | Removing and re-adding the class resets the animation; browser handles this gracefully |
| `querySelector` with attribute selector is O(n) in DOM — performance on 100+ pipelines | Low | Low | Dashboard is re-rendered via `innerHTML` on each SSE update; flash is triggered once per completion and queries a single element |

## Security Considerations

- `data-id` attribute is HTML-escaped via `esc(id)` — no XSS vector.
- No pipeline IDs are embedded in JS string contexts in the flash function; the attribute selector uses the escaped string from the DOM, not from JS variables.
- No server-side changes; no new attack surface.

## Dependencies

- Sprint 017 (in_progress) — introduces `dashPipelineData()` as the shared helper; this sprint modifies it. Sprint 018 should be implemented after Sprint 017 is complete and merged, or as a combined patch.

## Open Questions

1. Should the initial-load `completed` pipelines flash? The proposed answer is **no** — guard with `initialLoadDone` flag.
2. Should the flash also appear in list-view rows? The proposed answer is **defer** — list rows are simpler elements; the `.dash-card` glow is card-specific. List rows already get the correct pct/count from the fix.
3. Should the stage count show a `✓` prefix for completed pipelines (e.g. `✓ 3 / 3 stages`)? Proposed: **yes, simple addition** — `effectiveDone + '\u2009/\u2009' + totalStages` can be prefixed with `'\u2713\u2002'` when `status === 'completed'`.
