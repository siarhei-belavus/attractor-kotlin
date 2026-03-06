# Decision Log

Purpose: short record of non-obvious engineering decisions made in this project.

## Definitions

- `decision`: chosen technical approach for a concrete problem.
- `status`: `proposed` | `accepted` | `superseded` | `rejected`.

## Compact Format

Use one of these:

- one-line: `YYYY-MM-DD | status | decision | impact`
- two-line:
  - `YYYY-MM-DD | status | decision`
  - `why/impact: ...`

## Entries

- `2026-03-06 | accepted | status writes stay synchronous; debounce only project_log | preserves state consistency while reducing SQLite log-write pressure`

