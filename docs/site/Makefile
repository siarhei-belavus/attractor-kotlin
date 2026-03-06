# =============================================================================
# attractor docs — Makefile
# =============================================================================
# Run from this directory (docs/site/) or from the repo root:
#   make -C docs/site <target>
#
# Requirements:
#   hugo       — https://gohugo.io/installation/
#   pagefind   — npm install -g pagefind   (only needed for index / serve-search)
#   node/npx   — https://nodejs.org/       (only needed for serve-search)
# =============================================================================

SHELL   := /bin/bash
PORT    ?= 1313
PUBLIC  := public

.PHONY: help build index serve serve-search clean

# Default target — show available targets
help:
	@echo ""
	@echo "  attractor docs"
	@echo ""
	@echo "  make build          Build the site into $(PUBLIC)/ (with minify)"
	@echo "  make index          Run Pagefind on $(PUBLIC)/ to generate the search index"
	@echo "  make serve          Live-reload dev server (no search index)"
	@echo "  make serve-search   Build + index + serve static site with search working"
	@echo "  make clean          Delete build output ($(PUBLIC)/ and resources/)"
	@echo ""
	@echo "  Options (pass on the command line):"
	@echo "    PORT=<n>   Server port  (default: $(PORT))"
	@echo ""

# Production build (minified, ready for deployment or pagefind indexing)
build:
	hugo --minify

# Run Pagefind on the built output to generate/update the search index.
# Requires a prior `make build`.
index:
	@command -v pagefind >/dev/null 2>&1 || npx --yes pagefind --version >/dev/null
	npx pagefind --site $(PUBLIC)

# Fast local dev server with live reload.
# Search will show "index not available" — use serve-search to test search locally.
serve:
	hugo server --port $(PORT) --bind 0.0.0.0

# Full local preview: build, index, then serve the static output.
# Search works here because Pagefind's JS can reach /pagefind/pagefind.js.
serve-search: build index
	@echo ""
	@echo "  Serving docs with search at http://localhost:$(PORT)"
	@echo "  Press Ctrl+C to stop."
	@echo ""
	npx --yes serve $(PUBLIC) --listen $(PORT) --single

# Remove all build output
clean:
	rm -rf $(PUBLIC) resources
