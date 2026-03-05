---
title: "CLI"
weight: 30
---

## Installation

### Build

```bash
make cli-jar
```

Produces `build/libs/attractor-cli-devel.jar`. For a versioned release: `make release`

### Run

```bash
# Via JAR directly
java -jar build/libs/attractor-cli-devel.jar [command]

# Via bin/ wrapper (auto-locates latest CLI JAR)
bin/attractor [command]
```

## Command Grammar

```
attractor [--host <url>] [--output text|json] [--help] [--version]
          <resource> <verb> [flags] [args]
```

## Global Flags

| Flag | Default | Description |
|------|---------|-------------|
| `--host <url>` | `http://localhost:7070` | Target Attractor server base URL |
| `--output text\|json` | `text` | Output format. Use `json` for machine-readable output (enables `jq` piping) |
| `--help` | — | Show help (works at any level) |
| `--version` | — | Print version string |

## Resources

### project — 15 commands

| Command | Flags | Description |
|---------|-------|-------------|
| `attractor project list` | | List all projects as a table (ID, Name, Status, Started) |
| `attractor project get <id>` | | Show all fields for a single project |
| `attractor project create` | `--file <path>` (required), `--name`, `--simulate`, `--no-auto-approve`, `--prompt` | Submit a DOT file and run it |
| `attractor project update <id>` | `--file <path>`, `--prompt` | Update DOT source or prompt |
| `attractor project delete <id>` | | Delete a non-running project |
| `attractor project rerun <id>` | | Restart a completed/failed project |
| `attractor project pause <id>` | | Pause a running project |
| `attractor project resume <id>` | | Resume a paused project |
| `attractor project cancel <id>` | | Cancel a running or paused project |
| `attractor project archive <id>` | | Move project to archive |
| `attractor project unarchive <id>` | | Restore project from archive |
| `attractor project stages <id>` | | List stage execution records |
| `attractor project family <id>` | | List all versions in the project's family |
| `attractor project watch <id>` | `--interval-ms` (default 2000), `--timeout-ms` | Poll until terminal state. Exit 0=completed, 1=failed/cancelled |
| `attractor project iterate <id>` | `--file <path>` (required), `--prompt` | Create a new family iteration |

### artifact — 7 commands

| Command | Flags | Description |
|---------|-------|-------------|
| `attractor artifact list <id>` | | List artifact files (path, size, type) |
| `attractor artifact get <id> <path>` | | Print artifact content to stdout |
| `attractor artifact download-zip <id>` | `--output <file>` | Download all artifacts as ZIP (default: artifacts-{id}.zip) |
| `attractor artifact stage-log <id> <nodeId>` | | Print stage live log to stdout |
| `attractor artifact failure-report <id>` | | Print failure report JSON |
| `attractor artifact export <id>` | `--output <file>` | Export project as ZIP (default: project-{id}.zip) |
| `attractor artifact import <file>` | `--on-conflict skip\|overwrite` | Import from an exported ZIP |

### dot — 8 commands

| Command | Flags | Description |
|---------|-------|-------------|
| `attractor dot generate` | `--prompt <text>` (required), `--output` | Generate DOT from natural language (synchronous) |
| `attractor dot generate-stream` | `--prompt <text>` (required) | Generate DOT with streaming token output |
| `attractor dot validate` | `--file <path>` (required) | Lint/validate a DOT file; print diagnostics |
| `attractor dot render` | `--file <path>` (required), `--output` | Render DOT to SVG (default: output.svg) |
| `attractor dot fix` | `--file <path>` (required), `--error <msg>` | Fix broken DOT using LLM (synchronous) |
| `attractor dot fix-stream` | `--file <path>` (required), `--error <msg>` | Fix DOT with streaming output |
| `attractor dot iterate` | `--file <path>` (required), `--changes <text>` (required) | Iterate on DOT with a change description (synchronous) |
| `attractor dot iterate-stream` | `--file <path>` (required), `--changes <text>` (required) | Iterate on DOT with streaming output |

### settings — 3 commands

| Command | Description |
|---------|-------------|
| `attractor settings list` | Show all settings as a table (Key, Value) |
| `attractor settings get <key>` | Print a single setting value |
| `attractor settings set <key> <value>` | Update a setting |

### models — 1 command

```bash
attractor models list
```

List all available LLM models (ID, Provider, Name, Context, Tools, Vision).

### events — 2 commands

| Command | Description |
|---------|-------------|
| `attractor events` | Stream all project events until Ctrl+C |
| `attractor events <id>` | Stream events for one project; exits when project reaches a terminal state |

## Exit Codes

| Code | Meaning |
|------|---------|
| `0` | Success |
| `1` | API error, connection error, or runtime error |
| `2` | Usage error (missing required argument, unknown command, invalid flag) |

## Workflow Examples

### 1. Submit a project, watch it, then download artifacts

```bash
# Submit
ID=$(attractor project create --file my-project.dot --output json | jq -r '.id')

# Watch until terminal state
attractor project watch "$ID"

# Download artifacts
attractor artifact download-zip "$ID"
```

### 2. Generate DOT from prompt, validate, then run

```bash
# Generate
attractor dot generate --prompt "Build and test a Go REST API" --output project.dot

# Validate
attractor dot validate --file project.dot

# Submit
attractor project create --file project.dot
```

### 3. Investigate a failed project

```bash
# Get the failure report
attractor artifact failure-report <id>

# Browse individual stage logs
attractor project stages <id>
attractor artifact stage-log <id> <nodeId>
```
