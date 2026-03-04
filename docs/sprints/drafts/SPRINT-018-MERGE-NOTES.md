# Sprint 018 Merge Notes

## Claude Draft Strengths
- Correctly identified the single fix point: `dashPipelineData()` (Sprint 017 shared helper)
- Detailed two-line fix for `pct` and `stageCountStr` with exact before/after code
- Both card and list layouts inherit the fix automatically via the shared function
- Flash animation scoped to `applyUpdate()` transition detection — no SSE logic changes
- Concrete 5-assertion test plan

## Codex Draft Strengths
- More conservative about the flash animation: treated it as "optional" with a clear gate
- Emphasis on `totalStages === 0` edge case not regressing (clean branch structure)
- Simple, clear DoD: 100% bar + N/N count + behaviour safety for other statuses
- Recommended against over-committing to animation scope

## Valid Critiques Accepted
1. **`data-id` + CSS attribute selector is fragile** — Claude's draft used `querySelector('[data-id="..."]')` with a lossily stripped ID. Replaced with `id="dash-card-{id}"` on the card element and `document.getElementById('dash-card-' + id)` in `flashDashCard()`. This mirrors the existing `id="dash-elapsed-{id}"` pattern already in the codebase.
2. **Initial-load flash suppression must be explicit in plan** — Claude's draft mentioned `initialLoadDone` flag only in Risks. Replaced with a `prevStatuses` map approach: track each pipeline's last known status; only flash when the pipeline was previously seen at a non-completed status (i.e. `prevStatuses[key] !== undefined && prevStatus !== 'completed'`). First-load pipelines that arrive already-completed are never flashed because `prevStatuses[key]` is `undefined`.
3. **Flash should be framed as optional in DoD** — DoD now separates "Bug Fix" (primary, required) from "Completion Flash" (enhancement, clearly labelled).

## Critiques Rejected
- **CSS variable convention** — Codex said hardcoded hex greens violate constraints. In practice, all status-specific colours in the codebase use hardcoded hex (`.dash-progress-fill.s-completed { background: #238636; }`, `.badge-completed { color: #3fb950; }`, etc.). The variable convention applies to structural tokens only (`--border`, `--surface`). Hardcoded greens in keyframes are consistent.
- **Build command portability** — Codex suggested `make build` / `./gradlew test`. The build command in DoD follows the MEMORY.md canonical form; no change needed.

## Interview Refinements Applied
- ✓ **Flash animation: YES** — user confirmed; included in sprint with proper scope as enhancement phase.
- ✓ **✓ prefix on stageCountStr** — user confirmed; `stageCountStr` for completed reads `✓ N / N stages`.

## Final Decisions
1. `pct` override: `status === 'completed' ? 100 : (totalStages > 0 ? ... : 0)` — completed is always 100%.
2. `stageCountStr` for completed: `'\u2713\u2002' + totalStages + '\u2009/\u2009' + totalStages + ' stages'`.
3. Flash via `id="dash-card-{id}"` on `.dash-card` + `document.getElementById()` in `flashDashCard(id)`.
4. Flash suppression via `prevStatuses` map, not `initialLoadDone` flag.
5. CSS keyframes use hardcoded green hex (consistent with project pattern).
6. Codex's caution on animation scope respected: flash is Phase 2 (enhancement), not Phase 1 (bug fix).
7. Test assertions: 5, targeting both bug fix symbols and flash symbols.
