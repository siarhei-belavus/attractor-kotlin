# Sprint 018: Pipeline Completion State - Progress Bar and UX Clarity

## Overview

The dashboard currently computes stage progress from stage-level statuses only (`completed` stages divided by total stages). During terminal transition windows, the pipeline-level status can already be `completed` while the final stage remains `running` in the same SSE payload. That creates a conflicting UI: green completed badge/top bar, but a partially filled progress bar and an undercounted stage summary (for example `2 / 3 stages`).

This sprint removes that ambiguity by making completion authoritative in dashboard presentation: when pipeline status is `completed`, progress must render as `100%` and stage count must render as `N / N stages`. The change is intentionally narrow, implemented at the shared dashboard view-model derivation point (`dashPipelineData()`), so both card and list views inherit the fix automatically.

An optional small completion affordance may be added if low-risk and bounded, but the primary acceptance target is correctness and clarity, not animation complexity.

## Use Cases

1. **Terminal clarity**: A user sees a just-finished pipeline and can immediately tell it is fully complete because the progress bar is fully green.
2. **No contradictory signals**: A user sees a completed status badge and matching stage count (`N / N`) rather than mixed messaging (`completed` + `N-1 / N`).
3. **Layout parity**: A user switching between card and list dashboard layouts sees identical completion behavior in both views.
4. **Non-completed fidelity**: A running, failed, or cancelled pipeline continues to show actual counted progress without forced 100%.

## Architecture

### Single Fix Point

The bug and desired behavior converge in one function:

- `dashPipelineData(id)` in `WebMonitorServer.kt` (used by both `buildDashCards()` and `buildDashList()`)

Current issue:
- `pct` is based on `doneStages / totalStages`.
- Existing `status === 'completed' ? 100 : 0` fallback only applies when `totalStages === 0`.
- With `totalStages > 0`, completed pipelines can stay below 100.

Planned derivation rules:
- If `status === 'completed'` and `totalStages > 0`:
  - force `pct = 100`
  - force display count to `totalStages / totalStages`
- If `status === 'completed'` and `totalStages === 0`:
  - keep existing 100% fallback behavior
- Otherwise:
  - preserve existing counted-progress behavior for all non-completed statuses

### Optional Completion Affordance (Bounded)

If included, affordance must remain local to dashboard client rendering and avoid new server contracts:

- Detect transition to `completed` in `applyUpdate()`
- Mark ID in a short-lived map/set with timestamp
- Add class token in row/card render path when recently completed
- CSS-only highlight pulse on `.dash-progress-fill` or card top stripe

This remains optional and must not expand scope beyond current files.

## Implementation Plan

### Phase 1: Fix completion-derived progress and count (~40%)

**Files:**
- `src/main/kotlin/attractor/web/WebMonitorServer.kt` - Modify

**Tasks:**
- [ ] Update `dashPipelineData(id)` to make pipeline `completed` status authoritative for progress width.
- [ ] Ensure `stageCountStr` reports `N / N stages` for `completed` pipelines with one or more stages.
- [ ] Preserve existing behavior for non-completed statuses (`running`, `failed`, `cancelled`, `paused`, etc.).
- [ ] Keep zero-stage completed behavior at 100% without regression.

### Phase 2: Optional just-completed visual cue (~20%)

**Files:**
- `src/main/kotlin/attractor/web/WebMonitorServer.kt` - Modify (optional)

**Tasks:**
- [ ] Add transition detection in `applyUpdate()` for `status` changes into `completed`.
- [ ] Track recent completion for a brief window (for example ~1.5s).
- [ ] Add CSS class and short animation emphasizing successful completion.
- [ ] Ensure effect is subtle and does not alter long-term steady-state visuals.

### Phase 3: Browser API regression coverage (~40%)

**Files:**
- `src/test/kotlin/attractor/web/WebMonitorServerBrowserApiTest.kt` - Modify

**Tasks:**
- [ ] Add at least 3 markup-presence assertions for new/updated symbols used by completion logic.
- [ ] Include assertions for completion override expression and stage-count override marker strings.
- [ ] If optional affordance is implemented, add assertions for its JS/CSS class token.
- [ ] Keep tests style-consistent with existing string-presence checks in the same suite.

## Files Summary

| File | Action | Purpose |
|------|--------|---------|
| `src/main/kotlin/attractor/web/WebMonitorServer.kt` | Modify | Correct completion progress/count derivation in shared dashboard data path; optionally add brief completion highlight |
| `src/test/kotlin/attractor/web/WebMonitorServerBrowserApiTest.kt` | Modify | Add markup-presence assertions guarding completion progress/count behavior and optional cue markers |

## Definition of Done

### Completion Clarity
- [ ] Completed pipelines always render `100%` progress width in dashboard card and list modes.
- [ ] Completed pipelines display `N / N stages` when `totalStages > 0`.
- [ ] No contradictory state remains between completed badge/top stripe and progress/count display.

### Behavior Safety
- [ ] Running/failed/cancelled/paused pipelines still use actual counted progress.
- [ ] Zero-stage completed pipelines remain 100% and do not regress.
- [ ] Sorting, tab selection, archive/delete actions, and elapsed timer behavior remain unchanged.

### Testing and Scope
- [ ] Existing tests pass.
- [ ] At least 3 new browser markup-presence assertions are added.
- [ ] No new Gradle dependencies.
- [ ] Changes remain confined to `WebMonitorServer.kt` and `WebMonitorServerBrowserApiTest.kt`.

## Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Override accidentally affects non-completed statuses | Low | Medium | Gate override strictly on `status === 'completed'` |
| Stage count override introduces off-by-one or zero-stage regression | Low | Medium | Keep explicit `totalStages > 0` branch and preserve existing zero-stage fallback |
| Optional visual cue adds complexity or visual noise | Medium | Low | Keep cue time-boxed, CSS-only, and optional to ship |
| Markup-presence tests become brittle due wording changes | Medium | Low | Assert stable symbol/class markers rather than long literal snippets |

## Security Considerations

- No new endpoints, storage, or permissions are introduced.
- No user-controlled content paths are newly interpolated by this sprint.
- All changes are presentation-side logic and styling within existing dashboard code paths.

## Dependencies

- Sprint 017 (in progress): `dashPipelineData()` shared by card/list rendering; this sprint relies on that shared derivation path.
- Sprint 015: Browser markup-presence test pattern in `WebMonitorServerBrowserApiTest.kt` is reused.

## Open Questions

1. Should the optional “just completed” affordance ship in Sprint 018, or be deferred to keep this sprint strictly bug-fix scoped?
2. If shipped, should the cue target the progress bar only (lowest visual risk) or card/list container (higher prominence)?
3. Should completed stage count remain plain `N / N stages` or add a checkmark prefix in a follow-up UX sprint?
