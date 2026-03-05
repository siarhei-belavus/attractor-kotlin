# Sprint 022: Hugo Docs Microsite on GitHub Pages

## Overview

Attractor's user-facing documentation lives today as Kotlin string literals inside
`WebMonitorServer.kt`, assembled into a five-tab HTML page served at runtime under `/docs`. While
functional for an offline-first embedded tool, this approach has real costs: documentation changes
require recompiling and redeploying the server, content is HTML-escaped inside Kotlin strings
(making editing error-prone), and there is no public URL to share or index.

This sprint extracts the entire documentation into a standalone Hugo static site living at
`docs-site/` in this repository. A scoped GitHub Actions workflow (`docs.yml`) triggers only
when files under `docs-site/**` change on `main`, builds Hugo, and publishes the result to GitHub
Pages. The in-app `/docs` endpoint is kept unchanged for air-gapped and offline use, but gains a
visible banner linking to the canonical public docs URL.

The result is a first-class documentation site at
`https://coreydaley.github.io/attractor/` that is trivially editable (plain Markdown), automatically
published, and independent of the Kotlin build.

## Use Cases

1. **Public documentation URL**: A user Googles "Attractor AI project orchestration" and lands on
   the GitHub Pages site — a readable, navigable reference with all five content sections.

2. **Easy doc editing**: A contributor wants to update the CLI reference. They open
   `docs-site/content/cli.md`, make the change, push to `main`. The site rebuilds and publishes
   automatically within ~60 seconds. No Kotlin knowledge needed.

3. **Offline / air-gapped use**: The in-app `/docs` page continues to work as before. Users on
   an isolated network see a banner: "Full docs available at https://coreydaley.github.io/attractor/"
   — but all content is still present inline.

4. **PR preview validation**: The `docs.yml` workflow runs `hugo build` on pull requests that
   touch `docs-site/**` but skips the deploy step, catching build errors before merge.

5. **REST API reference**: The existing `docs/api/rest-v1.md` is symlinked or copied into the
   Hugo site's content directory so that the REST API documentation is single-sourced.

## Architecture

```
Repository layout (new directory):

  docs-site/                        ← Hugo project root
  ├── config.toml                   ← Hugo config (baseURL, theme, params)
  ├── content/                      ← Markdown source pages
  │   ├── _index.md                 ← Home / getting-started redirect
  │   ├── web-app.md                ← Web App tab content
  │   ├── rest-api.md               ← REST API tab content
  │   ├── cli.md                    ← CLI tab content
  │   ├── dot-format.md             ← DOT Format tab content
  │   └── docker.md                 ← Docker tab content
  ├── static/                       ← Static assets (none initially)
  └── themes/                       ← Hugo theme (git submodule or inline)

GitHub Actions workflow:

  .github/workflows/docs.yml
    on:
      push:
        branches: [main]
        paths: ['docs-site/**']
      pull_request:
        branches: [main]
        paths: ['docs-site/**']

    jobs:
      build:
        - checkout (with submodules: true for theme)
        - setup Hugo
        - hugo --minify --source docs-site
        - upload artifact (if push to main)
      deploy:
        - needs: build
        - if: github.event_name == 'push' && github.ref == 'refs/heads/main'
        - deploy-pages action

Existing workflows unchanged:
  .github/workflows/ci.yml     — no paths filter added (unchanged behavior)
  .github/workflows/release.yml — unchanged

Theme choice: hugo-geekdoc
  - Minimal, docs-focused, no JS framework dependency
  - Installs as a downloaded release tarball (no git submodule required)
  - Supports left-nav sidebar with page weights for ordering
  - Dark mode out of the box

In-app docs update:
  WebMonitorServer.kt docsPageShell() — add one banner line below <div class="doc-header">:
  '<div style="background:var(--surface);border-bottom:1px solid var(--border);
    padding:6px 24px;font-size:0.82rem;color:var(--text-muted);text-align:center;">
    Full documentation: <a href="https://coreydaley.github.io/attractor/"
    target="_blank">coreydaley.github.io/attractor</a></div>'
```

## Implementation Plan

### Phase 1: Hugo site scaffolding (~20%)

**Files:**
- `docs-site/config.toml` — Create
- `docs-site/content/_index.md` — Create
- `docs-site/static/.gitkeep` — Create (ensures directory is tracked)

**Tasks:**
- [ ] Create `docs-site/` directory
- [ ] Write `docs-site/config.toml`:
  ```toml
  baseURL = "https://coreydaley.github.io/attractor/"
  title = "Attractor Docs"
  theme = "hugo-geekdoc"
  pluralizeListTitles = false

  [markup.goldmark.renderer]
    unsafe = true  # needed for raw HTML in some sections

  [markup.highlight]
    style = "github-dark"

  [params]
    geekdocNav = true
    geekdocSearch = false
    geekdocEditPath = "https://github.com/coreydaley/attractor/edit/main/docs-site/content"
  ```
- [ ] Download `hugo-geekdoc` theme release tarball into `docs-site/themes/hugo-geekdoc/`
  (alternative: include theme files directly so no submodule or download step is needed in CI)
- [ ] Write `docs-site/content/_index.md` with front matter weight=1 and a redirect or landing
  intro pointing to web-app as the first section
- [ ] Verify `hugo --source docs-site` builds without errors locally (or note as CI-only)

---

### Phase 2: Content extraction — 5 Markdown pages (~40%)

**Files:**
- `docs-site/content/web-app.md` — Create
- `docs-site/content/rest-api.md` — Create
- `docs-site/content/cli.md` — Create
- `docs-site/content/dot-format.md` — Create
- `docs-site/content/docker.md` — Create

**Approach**: Manually convert the HTML content from the five private functions in
`WebMonitorServer.kt` to idiomatic Markdown. This is a one-time transcription; going forward,
docs changes are made in Markdown only.

**Tasks:**
- [ ] Write `docs-site/content/web-app.md`:
  - Front matter: `title = "Web App"`, `weight = 10`
  - Sections: Getting Started, Navigation, Creating a Project (3 options), Project States,
    Monitoring, Action Buttons, Project Versions, Export ZIP Contents, Failure Diagnosis,
    Import/Export, Database Configuration, Settings
  - Tables use Markdown table syntax; code blocks use fenced backticks
  - Tip boxes become Markdown blockquotes with emoji prefix

- [ ] Write `docs-site/content/rest-api.md`:
  - Front matter: `title = "REST API"`, `weight = 20`
  - Sections: Overview, Error Codes, Project JSON shape, Endpoints (grouped: CRUD, Lifecycle,
    Versioning, Artifacts/Logs, Import/Export/DOT, DOT Operations, Settings, Models, Events/SSE)
  - HTTP method badges become bold method labels in Markdown (e.g., **GET**, **POST**)
  - Code blocks for curl examples
  - Note: this page covers the same content as `docs/api/rest-v1.md`; in a follow-up sprint,
    `rest-v1.md` can be removed or replaced with a symlink

- [ ] Write `docs-site/content/cli.md`:
  - Front matter: `title = "CLI"`, `weight = 30`
  - Sections: Installation, Command Grammar, Global Flags, Resources (project 14 cmds, artifact
    7, dot 8, settings 3, models 1, events 2), Exit Codes, Workflow Examples

- [ ] Write `docs-site/content/dot-format.md`:
  - Front matter: `title = "DOT Format"`, `weight = 40`
  - Sections: Overview, Node Types, Node Attributes, Edge Attributes, Graph Attributes,
    Annotated Examples (4 examples: linear, conditional, parallel, human review)

- [ ] Write `docs-site/content/docker.md`:
  - Front matter: `title = "Docker"`, `weight = 50`
  - Content from `dockerTabContent()` in WebMonitorServer.kt

---

### Phase 3: GitHub Actions workflow (~25%)

**Files:**
- `.github/workflows/docs.yml` — Create

**Tasks:**
- [ ] Write `.github/workflows/docs.yml`:
  ```yaml
  name: Docs

  on:
    push:
      branches: [main]
      paths:
        - 'docs-site/**'
    pull_request:
      branches: [main]
      paths:
        - 'docs-site/**'

  permissions:
    contents: read
    pages: write
    id-token: write

  concurrency:
    group: pages
    cancel-in-progress: true

  jobs:
    build:
      name: Build Hugo site
      runs-on: ubuntu-latest
      steps:
        - name: Checkout
          uses: actions/checkout@v4

        - name: Setup Hugo
          uses: peaceiris/actions-hugo@v3
          with:
            hugo-version: '0.147.0'
            extended: true

        - name: Build
          run: hugo --minify --source docs-site

        - name: Upload Pages artifact
          if: github.event_name == 'push' && github.ref == 'refs/heads/main'
          uses: actions/upload-pages-artifact@v3
          with:
            path: docs-site/public

    deploy:
      name: Deploy to GitHub Pages
      needs: build
      if: github.event_name == 'push' && github.ref == 'refs/heads/main'
      runs-on: ubuntu-latest
      environment:
        name: github-pages
        url: ${{ steps.deployment.outputs.page_url }}
      steps:
        - name: Deploy to GitHub Pages
          id: deployment
          uses: actions/deploy-pages@v4
  ```
- [ ] Verify the workflow YAML is valid (no syntax errors)
- [ ] Note in PR that GitHub Pages must be configured in repository settings:
  **Settings → Pages → Source: GitHub Actions** (one-time manual step)
- [ ] The `concurrency` group prevents race conditions from rapid pushes

---

### Phase 4: In-app docs banner (~5%)

**Files:**
- `src/main/kotlin/attractor/web/WebMonitorServer.kt` — Modify

**Tasks:**
- [ ] In `docsPageShell()`, after the `<div class="doc-header">...</div>` block, insert a
  public docs link banner:
  ```html
  <div style="background:var(--surface-raised);border-bottom:1px solid var(--border);
    padding:5px 24px;font-size:0.82rem;color:var(--text-muted);text-align:center;">
    Full documentation available online at
    <a href="https://coreydaley.github.io/attractor/" target="_blank" rel="noopener">
    coreydaley.github.io/attractor</a>
  </div>
  ```
- [ ] The in-app content remains fully present — this is purely additive

---

### Phase 5: .gitignore update (~5%)

**Files:**
- `.gitignore` — Modify

**Tasks:**
- [ ] Add `docs-site/public/` to `.gitignore` so that local Hugo build output is never
  accidentally committed (the CI/CD pipeline handles publishing)
- [ ] Add `docs-site/resources/` (Hugo cache directory)

---

### Phase 6: README update (~5%)

**Files:**
- `README.md` — Modify

**Tasks:**
- [ ] Add a Docs section near the top of the README linking to the GitHub Pages site
- [ ] Brief description: "Full documentation at https://coreydaley.github.io/attractor/"

---

## Files Summary

| File | Action | Purpose |
|------|--------|---------|
| `docs-site/config.toml` | Create | Hugo configuration (baseURL, theme, params) |
| `docs-site/content/_index.md` | Create | Home/landing page |
| `docs-site/content/web-app.md` | Create | Web App documentation |
| `docs-site/content/rest-api.md` | Create | REST API reference |
| `docs-site/content/cli.md` | Create | CLI reference |
| `docs-site/content/dot-format.md` | Create | DOT format guide with examples |
| `docs-site/content/docker.md` | Create | Docker deployment guide |
| `docs-site/themes/hugo-geekdoc/` | Create | Theme files (or downloaded in CI) |
| `.github/workflows/docs.yml` | Create | Hugo build + GitHub Pages deploy |
| `src/main/kotlin/attractor/web/WebMonitorServer.kt` | Modify | Add public docs link banner |
| `.gitignore` | Modify | Exclude `docs-site/public/` and `docs-site/resources/` |
| `README.md` | Modify | Add Docs link |

## Definition of Done

### Hugo Site
- [ ] `docs-site/` directory present and buildable with `hugo --source docs-site --minify`
- [ ] Five content pages present: web-app, rest-api, cli, dot-format, docker
- [ ] All content from the five in-app tabs is represented in the Markdown pages
- [ ] `docs-site/public/` excluded from git via `.gitignore`
- [ ] Site renders with working navigation between all five sections

### GitHub Actions
- [ ] `docs.yml` workflow exists and is valid YAML
- [ ] Workflow triggers only on changes to `docs-site/**` (not on Kotlin changes)
- [ ] Build job runs on both push and PR
- [ ] Deploy job runs only on push to `main` (not on PRs)
- [ ] `concurrency` group prevents race conditions
- [ ] Existing `ci.yml` and `release.yml` are unmodified

### GitHub Pages
- [ ] (Manual one-time step documented) Repository Settings → Pages → Source: GitHub Actions
- [ ] After first push, site is live at `https://coreydaley.github.io/attractor/`
- [ ] All five section pages are accessible via the navigation

### In-App Docs
- [ ] Public docs link banner appears in the in-app `/docs` page
- [ ] All existing in-app content is preserved (banner is additive only)
- [ ] Kotlin build still passes: `./gradlew build --no-daemon`
- [ ] All existing tests pass (no regressions from banner addition)

### Quality
- [ ] `docs-site/public/` is not committed to the repository
- [ ] README updated with docs link

## Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Hugo theme unavailable or breaking change | Low | Medium | Pin Hugo version in workflow (`hugo-version: '0.147.0'`); include theme files directly in repo rather than as submodule |
| GitHub Pages not enabled in repo settings | Medium | High | Document the one-time manual step clearly in the PR; the workflow self-documents the `environment: github-pages` requirement |
| `peaceiris/actions-hugo` action deprecation | Low | Low | Can swap to `hugo/actions` or manual install; Hugo binary is a simple download |
| Path filter misses a change that requires doc update | Low | Low | Path filter is for automation only; PRs that update both source and docs trigger both `ci.yml` and `docs.yml` |
| Kotlin string HTML → Markdown conversion loses formatting | Medium | Low | Manual review of all 5 pages on the published site; the in-app version remains as the authoritative reference during transition |
| `baseURL` mismatch breaks navigation in Hugo | Low | Medium | Test locally or in CI with `hugo --baseURL /` for relative paths; Pages deployment sets baseURL correctly |
| Concurrency cancel-in-progress terminates a deploy mid-flight | Low | Low | GitHub Pages deploy is idempotent; next push will redeploy cleanly |

## Security Considerations

- The workflow has minimal permissions: `contents: read`, `pages: write`, `id-token: write`
  (OIDC token for Pages deployment — no stored secrets needed)
- No user-controlled input flows into the Hugo build
- The `docs-site/` directory contains only static Markdown and config — no executable code
- The in-app banner uses a hardcoded URL (no user-controlled content)
- Path traversal is not applicable to a static site generator

## Dependencies

- Sprint 021 (completed) — stable codebase, no in-progress structural changes
- External: Hugo binary (installed by CI action, not a Gradle dep)
- External: `peaceiris/actions-hugo@v3` GitHub Action
- External: `actions/upload-pages-artifact@v3` and `actions/deploy-pages@v4` GitHub Actions
- External: `hugo-geekdoc` theme (MIT licensed)
- One-time manual: Enable GitHub Pages in repository settings (Source: GitHub Actions)

## Open Questions

1. **In-app `/docs` endpoint**: Should it remain as-is (current plan: yes, with banner) or be
   redirected to the GitHub Pages URL? A redirect would simplify maintenance but breaks offline use.

2. **Theme**: `hugo-geekdoc` chosen for simplicity. Is there a preference for a different theme
   (PaperMod, Docsy, or minimal custom)? Theme can be swapped without affecting content.

3. **`docs/api/rest-v1.md`**: Keep in parallel, or replace with a symlink/redirect to the Hugo
   site's REST API page? Proposed: keep for now, unify in a follow-up.

4. **Workflow on PR**: Build-only on PRs gives validation but no preview URL. Would the team
   benefit from Netlify/Cloudflare Pages deploy previews on PRs? Proposed: out of scope for v1.

5. **Docker tab content**: The `dockerTabContent()` function content was not fully visible during
   drafting — should be verified against the actual function before writing `docker.md`.
