# Sprint 022: Hugo Docs Microsite on GitHub Pages

## Overview

Attractor’s user-facing documentation currently lives as embedded HTML inside
`src/main/kotlin/attractor/web/WebMonitorServer.kt`, served at runtime at `/docs`. This keeps the
app self-contained (works offline) but makes docs hard to edit, review, and evolve: simple wording
changes require Kotlin string escaping and a full build/release cycle.

This sprint extracts the entire `/docs` content into a standalone Hugo microsite living in-repo at
`docs-site/`, built into a static site with real navigation, search, and a maintainable Markdown
content structure. A dedicated GitHub Actions workflow deploys the site to GitHub Pages on pushes
to `main`, but only when files under `docs-site/**` change, keeping existing CI and release
workflows untouched.

The in-app `/docs` endpoint remains supported for offline / air-gapped usage, but should clearly
link to (or optionally redirect to) the public GitHub Pages docs.

## Use Cases

1. **Public docs browsing**: A user visits `https://coreydaley.github.io/attractor/` and navigates
   Web App, REST API, CLI, DOT Format, and Docker docs with stable URLs and side navigation.
2. **Docs editing via PR**: A contributor edits Markdown under `docs-site/content/` and opens a PR;
   CI builds the docs site and fails fast on broken front matter, links, or Hugo errors.
3. **Auto-deploy on docs change**: A commit that touches only `docs-site/**` triggers a Pages deploy
   without impacting the existing Java CI (`ci.yml`) or tagged release workflow (`release.yml`).
4. **Offline fallback**: An operator running Attractor without internet can still use `/docs` inside
   the app, but sees an “Online docs” link when connectivity exists.

## Architecture

### Content ownership model

- **Hugo site source of truth:** `docs-site/content/**` (Markdown + front matter).
- **In-app `/docs`:** kept for offline usage; not automatically derived from Hugo in this sprint.
  (Optionally later: generate `/docs` HTML from Hugo output as a build artifact, but that is out of
  scope here.)

### Hugo site layout (proposed)

```text
docs-site/
  hugo.toml
  content/
    _index.md
    web-app/
      _index.md
      getting-started.md
      navigation.md
      create.md
      monitor.md
      projects.md
      export-import.md
      settings.md
    rest-api/
      _index.md
      overview.md
      endpoints.md           (or split by area)
    cli/
      _index.md
      install.md
      commands.md
      examples.md
    dot-format/
      _index.md
      overview.md
      nodes.md
      edges.md
      attributes.md
      examples.md
    docker/
      _index.md
      quickstart.md
      configuration.md
  static/
    images/                  (if needed)
```

The exact page breakdown can be adjusted during content porting, but the top-level sections must
map 1:1 with the existing `/docs` tabs.

### GitHub Pages deployment (Actions-based)

Add a new workflow `.github/workflows/docs.yml`:

- Triggers:
  - `push` to `main` with `paths: [ "docs-site/**" ]` → build + deploy
  - `pull_request` to `main` with `paths: [ "docs-site/**" ]` → build only (no deploy)
- Build:
  - install Hugo (likely extended build, depending on theme)
  - run `hugo --minify` from `docs-site/` into `docs-site/public/` (or `docs-site/dist/`)
- Deploy:
  - `actions/configure-pages`
  - `actions/upload-pages-artifact`
  - `actions/deploy-pages`

This avoids committing generated HTML to `main` and keeps deploy logic isolated from the Java
pipeline.

### Base URL and repository Pages path

The site must build with `baseURL = "https://coreydaley.github.io/attractor/"` so that assets and
links work under the repo subpath (not the domain root).

## Implementation Plan

### Phase 1: Hugo site scaffold + theme selection (~25%)

**Files:**
- `docs-site/hugo.toml` — Create
- `docs-site/content/_index.md` — Create
- `docs-site/content/**` — Create (section stubs)
- `docs-site/themes/**` or `docs-site/go.mod` (depending on theme strategy) — Create
- `docs/sprints/drafts/SPRINT-022-CODEX-DRAFT.md` — Create

**Tasks:**
- [ ] Create `docs-site/` with a minimal Hugo site that builds locally.
- [ ] Pick a docs-focused theme with:
  - left-side navigation
  - built-in search (or easy add-on)
  - no exotic build steps beyond Hugo (preferred)
- [ ] Configure:
  - `baseURL` for GitHub Pages repo path
  - clean permalinks (stable URLs)
  - section menus for the 5 top-level areas
- [ ] Add placeholder section index pages (`web-app`, `rest-api`, `cli`, `dot-format`, `docker`).

### Phase 2: Port docs content from Kotlin HTML to Markdown (~45%)

**Files:**
- `src/main/kotlin/attractor/web/WebMonitorServer.kt` — Reference source (no large refactor in this sprint)
- `docs-site/content/web-app/**` — Create
- `docs-site/content/rest-api/**` — Create
- `docs-site/content/cli/**` — Create
- `docs-site/content/dot-format/**` — Create
- `docs-site/content/docker/**` — Create

**Tasks:**
- [ ] Extract content from:
  - `webAppTabContent()`
  - `restApiTabContent()`
  - `cliTabContent()`
  - `dotFormatTabContent()`
  - `dockerTabContent()`
- [ ] Rewrite as clean Markdown (not HTML pasted into Markdown) with:
  - code fences for commands
  - tables where appropriate
  - internal links between sections
- [ ] Preserve key behavioral details and examples (commands, endpoints, workflows).
- [ ] Ensure the site renders without broken shortcodes/HTML warnings.

**Notes on REST API source**

There is already `docs/api/rest-v1.md`. For this sprint, prefer making the Hugo site’s REST API
section fully self-contained under `docs-site/content/rest-api/` so that the Pages deploy remains
scoped to `docs-site/**` only. If we want `docs/api/rest-v1.md` to remain canonical, that’s a
follow-up decision that requires expanding the workflow path filter.

### Phase 3: GitHub Actions workflow to build + deploy Pages (~20%)

**Files:**
- `.github/workflows/docs.yml` — Create

**Tasks:**
- [ ] Add `docs.yml` that:
  - builds on PRs affecting `docs-site/**`
  - deploys on `main` pushes affecting `docs-site/**`
- [ ] Ensure deploy does not interfere with `ci.yml` or `release.yml`.
- [ ] Confirm correct Pages configuration assumptions:
  - uses GitHub Actions deployment (not “Deploy from branch”)
  - publishes the uploaded artifact as the Pages site

### Phase 4: In-app `/docs` handling (~10%)

**Files:**
- `src/main/kotlin/attractor/web/WebMonitorServer.kt` — Modify

**Tasks:**
- [ ] Add a prominent “Online docs” link in the `/docs` header that points to the Pages URL.
- [ ] Decide on redirect behavior:
  - default: keep offline docs and link out
  - optional: allow redirect via a config/env var (e.g. `ATTRACTOR_DOCS_REDIRECT_URL`)

## Files Summary

| File | Action | Purpose |
|------|--------|---------|
| `docs-site/**` | Create | Hugo site source (content + config + theme) |
| `.github/workflows/docs.yml` | Create | Build + deploy docs site to GitHub Pages (path-scoped) |
| `src/main/kotlin/attractor/web/WebMonitorServer.kt` | Modify | Add link/optional redirect for `/docs` |
| `docs/sprints/drafts/SPRINT-022-CODEX-DRAFT.md` | Create | Sprint plan draft (this document) |

## Definition of Done

- [ ] `docs-site/` exists at repo root and `hugo` builds successfully locally.
- [ ] All 5 doc areas exist as Markdown under `docs-site/content/` with usable navigation.
- [ ] `.github/workflows/docs.yml` deploys to GitHub Pages on pushes to `main` that change
      `docs-site/**`.
- [ ] `.github/workflows/docs.yml` runs build-only checks on PRs that change `docs-site/**`.
- [ ] The published Pages site renders all sections with correct baseURL (works under
      `/attractor/`).
- [ ] Existing `ci.yml` and `release.yml` behavior remains unchanged.
- [ ] `/docs` in-app page links to the public docs (and optional redirect behavior is explicitly
      defined).

## Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Theme choice requires Hugo extended / extra tooling | Medium | Medium | Pick a theme compatible with Hugo extended only; install extended in workflow explicitly |
| Porting HTML → clean Markdown is time-consuming | Medium | Medium | Split into pages; prioritize correctness and completeness over perfect prose in first pass |
| baseURL / subpath breaks assets and links | Medium | High | Set `baseURL` to repo Pages URL; validate with a local build using `--baseURL` override |
| Deploy workflow doesn’t trigger due to path filter | Low | High | Add an explicit `paths:` filter and a small README/change to validate trigger behavior |
| In-app docs diverge from public docs over time | Medium | Medium | Add a banner noting the offline docs may be stale; treat public site as primary |

## Security Considerations

- Docs site is static output with no runtime secrets.
- GitHub Actions workflow should use minimal permissions:
  - read-only for checkout/build steps
  - Pages deploy permissions only in the deploy job.
- Avoid embedding any sensitive config examples in docs (API keys should remain env vars).

## Dependencies

- None on prior in-progress sprints (Sprints 019–021 are complete).
- Requires GitHub repository Pages to be enabled and configured for GitHub Actions deployment.
- Requires Hugo available locally for verification (CI installs it).

## Open Questions

1. Should `/docs` redirect to Pages by default, or remain offline-first with a link-out?
2. Which Hugo theme best matches the project’s tone and desired UX (book-style nav + search)?
3. Should REST API docs be canonical in `docs-site/` going forward, or should we keep
   `docs/api/rest-v1.md` canonical and expand the deploy workflow path filter accordingly?
4. Should `docs.yml` deploy only on `main` pushes, or also support manual dispatch
   (`workflow_dispatch`)?

