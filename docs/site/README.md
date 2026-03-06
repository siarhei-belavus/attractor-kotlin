# Attractor Docs Site

Hugo microsite published to GitHub Pages at https://attractor.coreydaley.dev.

## Requirements

- [Hugo extended](https://gohugo.io/installation/) v0.157.0+

## Build

```bash
hugo --minify --source docs-site
```

Output is written to `docs-site/public/` (git-ignored).

## Local preview

```bash
hugo server --source docs-site
```

Open http://localhost:1313 in your browser. The server reloads automatically on file changes.

## Content

All pages live in `docs-site/content/`. Each file is plain Markdown with a small front matter block:

```
---
title: "Page Title"
weight: 10
---
```

`weight` controls the order pages appear in the sidebar.

## Deployment

Pushes to `main` that touch any file under `docs-site/` automatically trigger the [Docs workflow](../.github/workflows/docs.yml), which builds and deploys to GitHub Pages.
