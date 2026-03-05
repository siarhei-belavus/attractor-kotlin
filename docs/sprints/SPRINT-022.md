# Sprint 022: Hugo Docs Microsite on GitHub Pages

## Overview

Attractor's user-facing documentation currently lives as Kotlin string literals inside
`WebMonitorServer.kt`, assembled into a five-tab HTML page served at the runtime path `/docs`.
While functional as an embedded reference, this approach has real costs: documentation changes
require recompiling and redeploying the entire server binary, content is buried in HTML-escaped
Kotlin strings (making edits error-prone and review difficult), and there is no public URL to
share, search, or index.

This sprint extracts the entire documentation into a standalone Hugo static site living at
`docs-site/` in this repository. A scoped GitHub Actions workflow (`docs.yml`) triggers only
when files under `docs-site/**` change on `main`, builds Hugo, and deploys the result to GitHub
Pages automatically. The in-app `/docs` endpoint and all its associated Kotlin code is removed
entirely — the public site becomes the single canonical documentation reference.

The result is a first-class documentation site at `https://coreydaley.github.io/attractor/` that
is trivially editable (plain Markdown), automatically published on every relevant push, and fully
independent of the Kotlin build and release cycle. Existing `ci.yml` and `release.yml` workflows
are completely unaffected.

## Use Cases

1. **Public documentation URL**: A user searches for Attractor docs and lands on
   `https://coreydaley.github.io/attractor/` — a readable, navigable Hugo site with all five
   content sections (Web App, REST API, CLI, DOT Format, Docker).

2. **Easy doc editing**: A contributor wants to update the CLI reference. They open
   `docs-site/content/cli.md`, make the change in plain Markdown, and push to `main`. The site
   rebuilds and publishes automatically within ~60 seconds. No Kotlin knowledge needed.

3. **PR validation**: A PR that touches `docs-site/**` triggers the build-only job — broken front
   matter, Hugo errors, or invalid Markdown are caught before merge. The deploy job is skipped on
   PRs.

4. **Manual re-deploy**: A maintainer can trigger the docs deploy manually via `workflow_dispatch`
   (e.g., after updating GitHub Pages settings) without needing a content push.

5. **Smaller server binary**: Removing the `/docs` endpoint and its embedded HTML/CSS/JS from
   `WebMonitorServer.kt` reduces binary size and eliminates a maintenance surface. The server
   focuses solely on runtime functionality.

## Architecture

```
Repository layout (new directory):

  docs-site/                        ← Hugo project root
  ├── hugo.toml                     ← Hugo config (baseURL, theme, params)
  ├── content/                      ← Markdown source pages
  │   ├── _index.md                 ← Home / landing page
  │   ├── web-app.md                ← Web App tab → Markdown
  │   ├── rest-api.md               ← REST API tab → Markdown
  │   ├── cli.md                    ← CLI tab → Markdown
  │   ├── dot-format.md             ← DOT Format tab → Markdown
  │   └── docker.md                 ← Docker tab → Markdown
  ├── static/                       ← Static assets (empty for v1)
  └── themes/
      └── hugo-geekdoc/             ← Theme files committed directly (tarball extract)

GitHub Pages deployment:

  .github/workflows/docs.yml
    on:
      push:       [main] + paths: ['docs-site/**']   → build + deploy
      pull_request: [main] + paths: ['docs-site/**'] → build only
      workflow_dispatch:                               → build + deploy

    jobs:
      build:
        - checkout
        - setup Hugo 0.157.0 extended
        - hugo --minify --source docs-site
        - upload-pages-artifact (push to main or workflow_dispatch only)
      deploy:
        - needs: build
        - if: push to main OR workflow_dispatch
        - configure-pages
        - deploy-pages

Hugo config (hugo.toml):

  baseURL = "https://coreydaley.github.io/attractor/"
  theme = "hugo-geekdoc"
  [markup.highlight]
    style = "github-dark"
  [params]
    geekdocNav = true
    geekdocSearch = false
    geekdocEditPath = "https://github.com/coreydaley/attractor/edit/main/docs-site/content"

Kotlin changes:

  WebMonitorServer.kt — remove:
    - httpServer.createContext("/docs") { ... } handler
    - docsHtml()
    - docsPageShell()
    - webAppTabContent()
    - restApiTabContent()
    - cliTabContent()
    - dotFormatTabContent()
    - dockerTabContent()
    - All associated CSS rules (.doc-header, .doc-tab-bar, .doc-tab, .doc-panel,
      .badge, .badge-*, .endpoint, .endpoint-sig, .endpoint-path, .tip-box, .status-table)
    - The showTab() JS function and localStorage key 'attractor-docs-tab'
```

## Implementation Plan

### Phase 1: Hugo site scaffolding and theme (~15%)

**Files:**
- `docs-site/hugo.toml` — Create
- `docs-site/content/_index.md` — Create
- `docs-site/themes/hugo-geekdoc/` — Create (tarball extracted)
- `docs-site/static/.gitkeep` — Create
- `.gitignore` — Modify

**Tasks:**
- [ ] Create `docs-site/` directory structure
- [ ] Download and extract `hugo-geekdoc` theme release tarball (latest release from
  `https://github.com/thegeeklab/hugo-geekdoc`) into `docs-site/themes/hugo-geekdoc/`
  — commit theme files directly (no git submodule, no CI download step)
- [ ] Write `docs-site/hugo.toml`:
  ```toml
  baseURL = "https://coreydaley.github.io/attractor/"
  title = "Attractor Docs"
  theme = "hugo-geekdoc"
  pluralizeListTitles = false

  [markup.highlight]
    style = "github-dark"

  [params]
    geekdocNav = true
    geekdocSearch = false
    geekdocEditPath = "https://github.com/coreydaley/attractor/edit/main/docs-site/content"
  ```
- [ ] Write `docs-site/content/_index.md`:
  ```markdown
  ---
  title: Attractor Docs
  geekdocNav: false
  ---
  Attractor is an AI project orchestration system. Select a section from the navigation to get started.
  ```
- [ ] Add to `.gitignore`:
  ```
  docs-site/public/
  docs-site/resources/
  ```
- [ ] Verify `hugo --source docs-site` builds without errors (zero content pages is fine at this stage)

---

### Phase 2: Content extraction — 5 Markdown pages (~40%)

**Files:**
- `docs-site/content/web-app.md` — Create
- `docs-site/content/rest-api.md` — Create
- `docs-site/content/cli.md` — Create
- `docs-site/content/dot-format.md` — Create
- `docs-site/content/docker.md` — Create

**Approach**: Manually transcribe the HTML content from the five private functions in
`WebMonitorServer.kt` to clean, idiomatic Markdown. This is a one-time content migration.
Going forward, all documentation changes are made in Markdown only.

**Tasks:**
- [ ] Write `docs-site/content/web-app.md`:
  - Front matter: `title: "Web App"`, `weight: 10`
  - Source: `webAppTabContent()` in WebMonitorServer.kt
  - Sections: Getting Started, Navigation, Creating a Project (3 options), Project States,
    Monitoring, Action Buttons, Project Versions, Export ZIP Contents, Failure Diagnosis,
    Import/Export, Database Configuration, Settings (Execution Mode, Providers, CLI Templates,
    System Tools)
  - Convert tip boxes to Markdown blockquotes: `> **Note:** ...`
  - Convert tables to Markdown table syntax
  - Code blocks use fenced backticks

- [ ] Write `docs-site/content/rest-api.md`:
  - Front matter: `title: "REST API"`, `weight: 20`
  - Source: `restApiTabContent()` in WebMonitorServer.kt
  - Sections: Overview, Error Response Format, Project JSON Shape, Endpoints (Project CRUD,
    Lifecycle, Versioning, Artifacts & Logs, Import/Export/DOT, DOT Operations, Settings,
    Models, Events/SSE)
  - HTTP badges become bold inline labels: `**GET**`, `**POST**`, etc.
  - This page is self-contained in `docs-site/` — edits to `docs/api/rest-v1.md` do not
    automatically update this page (REST docs consolidation is a follow-up task)

- [ ] Write `docs-site/content/cli.md`:
  - Front matter: `title: "CLI"`, `weight: 30`
  - Source: `cliTabContent()` in WebMonitorServer.kt
  - Sections: Installation, Command Grammar, Global Flags, Resources (project, artifact, dot,
    settings, models, events), Exit Codes, Workflow Examples

- [ ] Write `docs-site/content/dot-format.md`:
  - Front matter: `title: "DOT Format"`, `weight: 40`
  - Source: `dotFormatTabContent()` in WebMonitorServer.kt
  - Sections: Overview, Node Types, Node Attributes, Edge Attributes, Graph Attributes,
    Annotated Examples (linear, conditional, parallel, human review)

- [ ] Write `docs-site/content/docker.md`:
  - Front matter: `title: "Docker"`, `weight: 50`
  - Source: `dockerTabContent()` in WebMonitorServer.kt
  - Content: Docker image, environment variables, docker-compose, .env file usage

- [ ] Run `hugo --source docs-site` — confirm all 5 pages render without errors

---

### Phase 3: GitHub Actions workflow (~20%)

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
    workflow_dispatch:

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
            hugo-version: '0.157.0'
            extended: true

        - name: Build
          run: hugo --minify --source docs-site

        - name: Upload Pages artifact
          if: |
            github.event_name == 'push' && github.ref == 'refs/heads/main' ||
            github.event_name == 'workflow_dispatch'
          uses: actions/upload-pages-artifact@v3
          with:
            path: docs-site/public

    deploy:
      name: Deploy to GitHub Pages
      needs: build
      if: |
        github.event_name == 'push' && github.ref == 'refs/heads/main' ||
        github.event_name == 'workflow_dispatch'
      runs-on: ubuntu-latest
      environment:
        name: github-pages
        url: ${{ steps.deployment.outputs.page_url }}
      steps:
        - name: Configure Pages
          uses: actions/configure-pages@v5

        - name: Deploy to GitHub Pages
          id: deployment
          uses: actions/deploy-pages@v4
  ```
- [ ] Verify YAML is valid (no syntax errors)

---

### Phase 4: Remove in-app `/docs` endpoint (~20%)

**Files:**
- `src/main/kotlin/attractor/web/WebMonitorServer.kt` — Modify

**Tasks:**
- [ ] Remove the `/docs` context handler: `httpServer.createContext("/docs") { ... }` block
- [ ] Remove private function `docsHtml()`
- [ ] Remove private function `docsPageShell()`
- [ ] Remove private function `webAppTabContent()`
- [ ] Remove private function `restApiTabContent()`
- [ ] Remove private function `cliTabContent()`
- [ ] Remove private function `dotFormatTabContent()`
- [ ] Remove private function `dockerTabContent()`
- [ ] Remove CSS rules for docs-only classes in the main stylesheet:
  - `.doc-header`, `.doc-header h1`, `.doc-header a`
  - `.doc-tab-bar`, `.doc-tab`, `.doc-tab.active`
  - `.doc-panel`, `.doc-panel.active`
  - `.badge`, `.badge-get`, `.badge-post`, `.badge-patch`, `.badge-put`, `.badge-delete`
  - `.endpoint`, `.endpoint-sig`, `.endpoint-path`
  - `.tip-box`
  - `.status-table`
- [ ] Remove the `showTab()` JS function body and the `window.onload` block that restored the docs
  tab from localStorage (key `'attractor-docs-tab'`) — these are only in the docs page shell
- [ ] Verify: `GET /docs` now returns 404 (falls through to the not-found handler)
- [ ] Run `./gradlew build --no-daemon` — must pass with no compiler warnings
- [ ] Run `./gradlew check --no-daemon` — all tests must pass

---

### Phase 5: README update (~5%)

**Files:**
- `README.md` — Modify

**Tasks:**
- [ ] Add a Docs link near the top of the README:
  ```markdown
  ## Documentation
  Full documentation: https://coreydaley.github.io/attractor/
  ```

---

## Files Summary

| File | Action | Purpose |
|------|--------|---------|
| `docs-site/hugo.toml` | Create | Hugo configuration (baseURL, theme, params) |
| `docs-site/content/_index.md` | Create | Landing page |
| `docs-site/content/web-app.md` | Create | Web App documentation |
| `docs-site/content/rest-api.md` | Create | REST API reference |
| `docs-site/content/cli.md` | Create | CLI reference |
| `docs-site/content/dot-format.md` | Create | DOT format guide with examples |
| `docs-site/content/docker.md` | Create | Docker deployment guide |
| `docs-site/themes/hugo-geekdoc/` | Create | Theme files (committed directly, no submodule) |
| `docs-site/static/.gitkeep` | Create | Ensures static/ is tracked |
| `.github/workflows/docs.yml` | Create | Hugo build + GitHub Pages deploy (path-scoped) |
| `.gitignore` | Modify | Exclude `docs-site/public/` and `docs-site/resources/` |
| `src/main/kotlin/attractor/web/WebMonitorServer.kt` | Modify | Remove `/docs` handler + all 7 private functions + docs CSS/JS |
| `README.md` | Modify | Add docs link |

## Definition of Done

### Hugo Site
- [ ] `docs-site/` directory present and buildable: `hugo --source docs-site --minify` exits 0
- [ ] Five content pages present: `web-app.md`, `rest-api.md`, `cli.md`, `dot-format.md`, `docker.md`
- [ ] All content from the five in-app tabs is represented faithfully in the Markdown pages
- [ ] `docs-site/public/` and `docs-site/resources/` excluded via `.gitignore`
- [ ] Navigation renders with all five sections accessible

### GitHub Actions
- [ ] `docs.yml` workflow exists and passes YAML lint
- [ ] Workflow triggers only on changes to `docs-site/**` (push/PR) plus `workflow_dispatch`
- [ ] Build job runs on push, PR, and workflow_dispatch
- [ ] Deploy job (configure-pages + deploy-pages) runs only on push to `main` or workflow_dispatch
- [ ] `concurrency: cancel-in-progress: true` prevents race conditions
- [ ] Existing `ci.yml` and `release.yml` are unmodified

### GitHub Pages
- [ ] (One-time manual step documented) Repository Settings → Pages → Source: GitHub Actions
- [ ] After first workflow run, site is live at `https://coreydaley.github.io/attractor/`
- [ ] All five section pages accessible via navigation
- [ ] Assets and links work under the `/attractor/` subpath (baseURL correct)

### In-App `/docs` Removal
- [ ] `GET /docs` returns 404 (no handler registered)
- [ ] All 7 private docs functions removed from `WebMonitorServer.kt`
- [ ] Docs-only CSS rules removed from the main stylesheet
- [ ] `showTab()` JS function and localStorage `attractor-docs-tab` references removed
- [ ] `./gradlew build --no-daemon` passes with no compiler warnings
- [ ] `./gradlew check --no-daemon` — all existing tests pass (no regressions)

### Quality
- [ ] `docs-site/public/` is NOT committed to the repository
- [ ] README updated with public docs link
- [ ] No new Gradle dependencies

## Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| GitHub Pages not enabled in repository settings | Medium | High | Document the one-time manual step; the `environment: github-pages` in the workflow makes it self-documenting |
| Hugo version `0.157.0` unavailable in `peaceiris/actions-hugo@v3` | Low | Medium | Check action's supported versions; fallback: install Hugo via curl in the workflow |
| `baseURL` subpath breaks asset links in Hugo site | Low | Medium | Verify locally with `hugo --source docs-site --baseURL /attractor/`; geekdoc handles subpaths correctly |
| Docs content drift after removal of in-app page | Low | Low | The Hugo site IS the canonical docs; in-app version is gone |
| CSS removal in WebMonitorServer.kt breaks main app UI | Low | High | Verify removed CSS classes are not used outside the deleted docs functions (search for `.doc-`, `.badge-`, `.endpoint`, `.tip-box` before deleting) |
| `concurrency: cancel-in-progress: true` cancels a live deploy | Low | Low | Pages deploy is idempotent; the next push will redeploy cleanly |
| REST API docs in `docs-site/` drift from `docs/api/rest-v1.md` | Medium | Low | `docs/api/rest-v1.md` remains for machine/API tooling; human-facing docs live in Hugo site. Follow-up: consider removing `rest-v1.md` or linking from it. |

## Security Considerations

- Workflow permissions are minimal: `contents: read`, `pages: write`, `id-token: write`
  (OIDC for Pages, no stored secrets)
- No user-controlled input reaches the Hugo build
- `docs-site/` contains only static Markdown and config — no executable code
- CSS removal from `WebMonitorServer.kt` must be verified to not touch styles used by the
  main app (search before delete)
- `markup.goldmark.renderer.unsafe` is NOT enabled — all content is clean Markdown

## Dependencies

- Sprints 019–021 all completed; codebase is stable with no in-progress structural changes
- External: Hugo `0.157.0` extended (installed by CI action)
- External: `peaceiris/actions-hugo@v3`, `actions/upload-pages-artifact@v3`,
  `actions/configure-pages@v5`, `actions/deploy-pages@v4`
- External: `hugo-geekdoc` theme (MIT licensed; committed directly to `docs-site/themes/`)
- One-time manual: Repository Settings → Pages → Source: GitHub Actions

## Open Questions

1. **`docs/api/rest-v1.md` future**: Keep as a machine-readable reference or deprecate now that
   Hugo site has the REST API content? Proposed: keep for now, unify in a follow-up sprint.

2. **Hugo Modules vs. tarball**: Using Hugo Modules (`go.mod`) is the modern approach for theme
   management but requires Go in CI. The tarball-commit approach (current plan) avoids this
   dependency. If Go is ever added to CI, prefer Hugo Modules in a follow-up.

3. **Section splitting**: The current plan keeps one file per section (e.g., `rest-api.md`). If
   the REST API section becomes unwieldy to navigate as a single page, split into
   `rest-api/` subdirectory in a follow-up sprint.
