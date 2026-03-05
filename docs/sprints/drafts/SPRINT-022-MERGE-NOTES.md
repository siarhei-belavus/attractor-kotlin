# Sprint 022 Merge Notes

## Claude Draft Strengths

- Strong path-scoped workflow design (`docs-site/**` filter isolates docs from Kotlin CI)
- Correct five-section content mapping (direct 1:1 with in-app tabs)
- PR build-only validation gate
- `.gitignore` hygiene for `docs-site/public/`
- `concurrency` group to prevent race conditions in Pages deploy
- Flat single-file-per-section content structure is simpler and sufficient for ~5 pages

## Codex Draft Strengths

- Identified `actions/configure-pages` step — missing from Claude's workflow
- Used `hugo.toml` (modern Hugo config filename over `config.toml`)
- Proposed section-based subdirectory layout as an alternative (valid for larger sites)
- Raised `workflow_dispatch` as a useful optional trigger
- Noted that pinning Hugo version needs verification

## Valid Critiques Accepted

1. **Add `actions/configure-pages`** — standard Pages workflow step that configures the Pages
   environment before upload; reduces "works on my repo" fragility
2. **Drop `markup.goldmark.renderer.unsafe = true`** — not needed when content is clean Markdown;
   removes unnecessary XSS surface
3. **Clarify theme vendoring strategy** — chose: download tarball in CI via workflow step; no git
   submodule; theme files committed directly to `docs-site/themes/` for reproducibility
4. **REST API docs: self-contained copy in `docs-site/`** — symlink/reference to
   `docs/api/rest-v1.md` outside `docs-site/**` would leave Pages stale on REST doc edits;
   the Hugo site owns its own copy of REST API content
5. **Pin current Hugo stable** — latest stable is `v0.157.0` (verified); use this instead of
   the speculated `0.147.0`
6. **Use `hugo.toml`** — modern Hugo config filename convention (still accepts `config.toml` but
   `hugo.toml` is preferred in Hugo 0.110+)
7. **Add `workflow_dispatch`** — small useful addition for manual re-deploys without a content push

## Critiques Rejected (with reasoning)

- **Subdirectory-per-section content structure**: Codex preferred multiple pages per section
  (e.g., `rest-api/overview.md`, `rest-api/endpoints.md`). Rejected for v1 — the content volume
  per section is modest enough that single-file sections are simpler to maintain, easier to
  search within, and require less Hugo config. A follow-up sprint can split if sections grow.
- **Offline fallback / link-out banner**: Codex suggested keeping `/docs` with a link-out banner.
  Interview answer was to **remove** the in-app `/docs` endpoint entirely. Claude's draft reflected
  the pre-interview position (banner); the merge reflects the post-interview decision (remove).

## Interview Refinements Applied

1. **Remove in-app `/docs` endpoint entirely** — delete the `/docs` route handler, the
   `docsHtml()`, `docsPageShell()`, `webAppTabContent()`, `restApiTabContent()`, `cliTabContent()`,
   `dotFormatTabContent()`, `dockerTabContent()` private functions, and all associated CSS/JS
   from `WebMonitorServer.kt`. This simplifies the Kotlin codebase and eliminates content drift.
2. **hugo-geekdoc theme** — confirmed; downloaded as tarball in CI (no submodule)
3. **Build-only on PRs** — confirmed; deploy only on push to `main`

## Final Decisions

- Directory: `docs-site/` (flat, 5 content files + `_index.md`)
- Theme: `hugo-geekdoc` (tarball committed to `docs-site/themes/`)
- Config: `docs-site/hugo.toml`
- Workflow: `.github/workflows/docs.yml` — build on PR + push, deploy on push only
- Hugo version: `0.157.0` (extended)
- In-app `/docs`: **removed entirely** from `WebMonitorServer.kt`
- REST API docs: self-contained copy in `docs-site/content/rest-api.md`
- `markup.goldmark.renderer.unsafe`: **not set** (clean Markdown only)
- `actions/configure-pages`: **added** to deploy job
- `workflow_dispatch`: **added** as trigger alongside push/PR
