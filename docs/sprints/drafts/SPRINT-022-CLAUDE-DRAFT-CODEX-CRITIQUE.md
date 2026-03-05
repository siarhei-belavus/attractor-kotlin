# Critique: SPRINT-022-CLAUDE-DRAFT

## Overall Assessment

Claude’s draft is aligned with the sprint intent and is close to implementation-ready: it scopes a
`docs-site/` Hugo microsite, adds a path-filtered GitHub Actions workflow for Pages, and keeps the
in-app `/docs` experience intact while pointing users at the canonical public site.

## What Works Well

1. **Intent match and scope discipline**: stays focused on Hugo + Pages + minimal in-app change;
   does not touch Gradle or introduce Kotlin dependencies.
2. **Correct workflow triggering strategy**: `paths: ['docs-site/**']` cleanly isolates docs
   publishing from `ci.yml` and `release.yml`.
3. **Offline-first posture**: keeping `/docs` unchanged (plus banner) satisfies the air-gapped
   use case without adding architecture risk.
4. **PR validation**: build-only on PRs that touch `docs-site/**` is a strong quality gate and
   matches the “don’t deploy on PRs” expectation.
5. **Pragmatic hygiene**: calling out `.gitignore` for Hugo output avoids accidentally committing
   generated `public/` content.

## Gaps / Concerns

1. **Single-sourcing REST API docs conflicts with the path-scoped deploy constraint**: symlinking
   or otherwise depending on `docs/api/rest-v1.md` means edits outside `docs-site/**` will not
   trigger the deploy workflow, leaving GitHub Pages stale. If the constraint is strict, the Hugo
   site should own a copy under `docs-site/content/` (or expand the workflow paths and accept the
   broader trigger).
2. **Theme acquisition strategy is inconsistent**: the draft mentions “submodule or inline”, then
   proposes downloading a release tarball into `docs-site/themes/`. Pick one approach and ensure
   the workflow matches it (e.g., `actions/checkout` with `submodules: true` if using submodules).
3. **`markup.goldmark.renderer.unsafe = true` is likely unnecessary and increases XSS foot-guns**:
   if the goal is “clean Markdown”, keep unsafe rendering off unless there’s a concrete Markdown
   rendering limitation that forces embedded HTML.
4. **Pages workflow missing `actions/configure-pages`**: many Pages deployments expect it (and it
   centralizes Pages config). It’s a low-cost add that reduces “works on my repo” fragility.
5. **Content structure may undershoot “proper navigation + structure”**: five mega-pages (`web-app.md`,
   `rest-api.md`, …) can work, but Hugo shines with smaller pages and section navigation. A plan
   that explicitly supports splitting large sections (especially Web App + REST API) reduces future
   refactor churn.
6. **Pinned Hugo version may be wrong at implementation time**: hardcoding `0.147.0` without
   confirming availability/support is brittle. Better to pin to “current stable at sprint start”
   (or use an action-provided default) and only hard-pin when you’ve validated the build.

## Recommended Adjustments

1. Decide and document the **canonical docs source**:
   - Option A (strict path-scope): copy REST API docs into `docs-site/content/rest-api/` and treat
     that as canonical going forward.
   - Option B (single-source outside docs-site): expand workflow `paths:` to include the canonical
     file(s), accepting a broader deploy trigger.
2. Choose a single **theme vendoring strategy** (commit theme, git submodule, Hugo module) and
   make the workflow reflect it explicitly.
3. Keep Markdown rendering safe by default; only enable `unsafe` if required and then constrain
   usage (document where raw HTML is allowed).
4. Add `actions/configure-pages` and verify the build output path matches what
   `upload-pages-artifact` publishes.
5. Recast Phase 2 as “port + split” for the largest areas so navigation/search are immediately
   useful (even if some pages start as stubs).

## Verdict

Directionally correct and very close. If the REST API single-sourcing decision and theme strategy
are clarified (and the workflow hardened with `configure-pages`), Claude’s plan is solid to execute
without surprises.

