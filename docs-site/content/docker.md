---
title: "Docker"
weight: 50
---

## Overview

Attractor ships as a multi-stage Docker image published to the GitHub Container Registry on every versioned release. The image bundles a Java 21 JRE, `graphviz`, and `git` — everything needed to run the server. SQLite data is stored in a mounted volume so it persists across container restarts.

## Quick Start

```bash
# Pull the latest release
docker pull ghcr.io/coreydaley/attractor:latest

# Run with a local data volume (SQLite persisted to ./data/attractor.db)
docker run --rm -p 7070:7070 -v "$(pwd)/data:/app/data" ghcr.io/coreydaley/attractor:latest
```

Open http://localhost:7070 once the container is running.

## Image Tags

Each release publishes three tags:

| Tag | Example | Notes |
|-----|---------|-------|
| `<major>.<minor>.<patch>` | `1.2.3` | Exact release — recommended for production |
| `<major>.<minor>` | `1.2` | Latest patch in this minor series |
| `<major>` | `1` | Latest release in this major series |

## Building Locally

```bash
make docker-build     # builds attractor:local
make docker-run       # runs attractor:local, auto-loads .env if present
```

Or directly with Docker:

```bash
docker build -t attractor:local .
docker run --rm -p 7070:7070 -v "$(pwd)/data:/app/data" attractor:local
```

## Passing API Keys

LLM provider API keys are passed as environment variables at run time — they are never baked into the image.

### Using a .env file (recommended)

```bash
cp .env.example .env
# edit .env and fill in your keys
make docker-run       # automatically passes --env-file .env when .env exists
```

Or with Docker directly:

```bash
docker run --rm -p 7070:7070 \
  -v "$(pwd)/data:/app/data" \
  --env-file .env \
  ghcr.io/coreydaley/attractor:latest
```

### Inline -e flags

```bash
docker run --rm -p 7070:7070 \
  -v "$(pwd)/data:/app/data" \
  -e ANTHROPIC_API_KEY=sk-ant-... \
  ghcr.io/coreydaley/attractor:latest
```

## Environment Variables

### LLM Provider API Keys

| Variable | Description |
|----------|-------------|
| `ANTHROPIC_API_KEY` | API key for Anthropic Claude (Direct API mode) |
| `OPENAI_API_KEY` | API key for OpenAI GPT (Direct API mode) |
| `GEMINI_API_KEY` | API key for Google Gemini (Direct API mode) |
| `GOOGLE_API_KEY` | Alternative to `GEMINI_API_KEY` |

### Custom OpenAI-Compatible API

These env vars bootstrap the custom provider settings on first start. Values saved through the Settings UI take precedence on subsequent starts.

| Variable | Default | Description |
|----------|---------|-------------|
| `ATTRACTOR_CUSTOM_API_ENABLED` | `false` | Set to `true` to enable the custom provider |
| `ATTRACTOR_CUSTOM_API_HOST` | `http://localhost` | Base URL of the OpenAI-compatible endpoint |
| `ATTRACTOR_CUSTOM_API_PORT` | `11434` | Port number (leave blank to omit from URL) |
| `ATTRACTOR_CUSTOM_API_KEY` | — | API key (optional — Ollama does not require one) |
| `ATTRACTOR_CUSTOM_API_MODEL` | `llama3.2` | Model name to use for requests |

> **Ollama tip:** If running Ollama as a separate container on the same Docker network, set `ATTRACTOR_CUSTOM_API_HOST` to the Ollama service name (e.g. `http://ollama`) rather than `localhost`.

### Database

By default the container uses SQLite at `/app/data/attractor.db` (set via `ATTRACTOR_DB_NAME`). To use MySQL or PostgreSQL instead, set the following variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `ATTRACTOR_DB_TYPE` | `sqlite` | `sqlite`, `mysql`, or `postgresql` |
| `ATTRACTOR_DB_HOST` | `localhost` | Database server hostname |
| `ATTRACTOR_DB_PORT` | — | Database port (defaults by type) |
| `ATTRACTOR_DB_NAME` | `attractor` | Database name (or SQLite file path) |
| `ATTRACTOR_DB_USER` | — | Database username |
| `ATTRACTOR_DB_PASSWORD` | — | Database password |
| `ATTRACTOR_DB_URL` | — | Full JDBC URL — overrides all individual `ATTRACTOR_DB_*` vars |

## Volumes

| Path | Description |
|------|-------------|
| `/app/data` | SQLite database and any other persistent data. Mount this to preserve state across restarts. |

## Ports

| Port | Description |
|------|-------------|
| `7070` | Web UI and REST API. Map with `-p <host-port>:7070`. |

## Docker Compose Example

```yaml
services:
  attractor:
    image: ghcr.io/coreydaley/attractor:latest
    ports:
      - "7070:7070"
    volumes:
      - attractor-data:/app/data
    environment:
      ANTHROPIC_API_KEY: ${ANTHROPIC_API_KEY}
      OPENAI_API_KEY: ${OPENAI_API_KEY}

volumes:
  attractor-data:
```

With Ollama on the same network:

```yaml
services:
  attractor:
    image: ghcr.io/coreydaley/attractor:latest
    ports:
      - "7070:7070"
    volumes:
      - attractor-data:/app/data
    environment:
      ATTRACTOR_CUSTOM_API_ENABLED: "true"
      ATTRACTOR_CUSTOM_API_HOST: "http://ollama"
      ATTRACTOR_CUSTOM_API_PORT: "11434"
      ATTRACTOR_CUSTOM_API_MODEL: "llama3.2"
    depends_on:
      - ollama

  ollama:
    image: ollama/ollama:latest
    volumes:
      - ollama-data:/root/.ollama

volumes:
  attractor-data:
  ollama-data:
```
