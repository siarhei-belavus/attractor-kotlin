# Sprint 022 Intent: Hugo Docs Microsite on GitHub Pages

## Seed

I want to extract the entire docs man nav site into its own Hugo-powered microsite that gets built
and published to a GitHub Pages site in this repository. I want the microsite to be on this branch
and part of this repository but get published automatically whenever a push occurs that updates any
content in the microsite directory.

## Context

Attractor's user-facing documentation is currently embedded as Kotlin string literals in
`WebMonitorServer.kt` (lines ~1388-6058), served at the runtime path `/docs`. The content spans
five logical sections: Web App, REST API, CLI, DOT Format, and Docker — each today rendered as a
tab in a single JavaScript-driven page.

Extracting this into a standalone Hugo microsite means:
- Documentation content lives as editable Markdown files (not Kotlin string escapes)
- Rendered by Hugo into a static site with proper navigation, search, and structure
- Published to GitHub Pages automatically whenever the `docs-site/` directory changes on `main`
- The original in-app `/docs` endpoint can redirect or link to the public site (or remain for
  offline/air-gapped use)

## Recent Sprint Context

- **Sprint 019**: Pipeline → Project rename (completed) — vocabulary stabilized
- **Sprint 020**: Workspace git versioning (completed) — per-project git repos, `workspace/` prefix
- **Sprint 021**: Git history panel in UI (completed) — REST endpoint + JS widget for git log

All three are completed. The codebase is in a stable state with no in-progress structural changes.

## Relevant Codebase Areas

- `src/main/kotlin/attractor/web/WebMonitorServer.kt` — contains docs content as private Kotlin
  functions: `webAppTabContent()`, `restApiTabContent()`, `cliTabContent()`,
  `dotFormatTabContent()`, `dockerTabContent()`, `docsPageShell()`, `docsHtml()`
- `docs/api/rest-v1.md` — existing REST API documentation markdown
- `.github/workflows/ci.yml` — existing CI workflow (push to main, PR to main)
- `.github/workflows/release.yml` — existing release workflow (push of `v*` tags)
- `docs/sprints/` — sprint management infrastructure

## Constraints

- Must follow project conventions (no new Gradle dependencies for Kotlin code)
- The microsite is pure static HTML/Hugo — no Kotlin build involvement
- Must not break existing `ci.yml` or `release.yml` workflows
- GitHub Pages publishing must be scoped to changes in the microsite directory only
- The microsite lives on `main` branch (not a separate `gh-pages` branch) using GitHub Pages
  "Deploy from branch" pointing to `docs-site/public/` OR the Actions-based `github-pages`
  artifact deployment approach

## Success Criteria

1. `docs-site/` directory exists in the repo root with a complete, buildable Hugo site
2. Content for all 5 documentation tabs (Web App, REST API, CLI, DOT Format, Docker) exists as
   Markdown source files under `docs-site/content/`
3. A GitHub Actions workflow (`docs.yml`) triggers on push to `main` when any file in
   `docs-site/**` changes, builds the Hugo site, and deploys to GitHub Pages
4. The published GitHub Pages site renders all documentation sections with working navigation
5. Existing `ci.yml` and `release.yml` workflows are unaffected
6. The in-app `/docs` endpoint is handled (redirected to GitHub Pages URL, or retained as-is)

## Verification Strategy

- Reference implementation: none (new capability)
- Spec: Hugo docs conventions + GitHub Pages deployment patterns
- Edge cases:
  - Hugo baseURL must match the GitHub Pages URL (`https://coreydaley.github.io/attractor/`)
  - Path-scoped workflow trigger must correctly filter `docs-site/**` paths
  - Hugo build must not fail on the extracted content
- Testing approach: build locally with `hugo --minify`, verify output structure

## Uncertainty Assessment

- Correctness uncertainty: Low — Hugo + GitHub Pages is a well-understood pattern
- Scope uncertainty: Medium — whether to retain/redirect/remove the in-app `/docs` endpoint
- Architecture uncertainty: Medium — Hugo theme selection, directory structure, whether to
  generate from the Kotlin strings vs. hand-rewrite as clean Markdown

## Open Questions

1. Should the in-app `/docs` endpoint be kept, redirected to GitHub Pages URL, or removed?
2. What directory name? `docs-site/` (preferred — avoids collision with existing `docs/`)
3. Should the GitHub Pages deployment use Actions artifact upload (modern) or `gh-pages` branch?
4. Which Hugo theme? (Suggest: Docsy, PaperMod, or the built-in `hugo-book` — all well-supported)
5. Should `docs/api/rest-v1.md` be incorporated into the Hugo site's REST API section?
6. Should the workflow also run on PRs (build-only, no deploy) for preview validation?
