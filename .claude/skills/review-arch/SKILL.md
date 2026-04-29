---
name: review-arch
description: Focused architecture + tests review. Dispatches only kotlin-architect and test-quality-reviewer on the local diff. Use when refactoring, restructuring modules, or adding non-domain Kotlin code where EMV/PCI scope does not apply.
allowed-tools: Bash(git diff*) Bash(git status*) Bash(git log*) Bash(git show*) Read Grep Glob
---

You are running a narrow review focused on architecture, code style, and test discipline.

## Step 1 — Capture the diff

Run in parallel:
- `git status --short`
- `git diff HEAD`
- `git diff --staged`

If empty, output `No changes to review.` and stop.

## Step 2 — Dispatch

Use the Agent tool to invoke, in a single parallel batch:
- `kotlin-architect`
- `test-quality-reviewer`

Prompt for each:
1. Full unified diff.
2. Pointer to `CLAUDE.md` at the repo root.
3. Reminder to follow the AGENT.md output format.

## Step 3 — Aggregate

Render both outputs verbatim with this header:

```
# Architecture + tests focused review

**Diff scope:** <N files, +X / -Y lines>
**Reviewers run:** kotlin-architect, test-quality-reviewer
```

Then both agent reports, then an aggregate verdict.

## Step 4 — No editing

Read-only.
