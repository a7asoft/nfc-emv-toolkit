---
name: review-pr
description: Run a multi-agent code review on a GitHub PR by number. Same agent set as /review but pulls the diff from the PR instead of the local working tree. Usage: /review-pr 42
argument-hint: "<pr-number>"
allowed-tools: Bash(gh pr view*) Bash(gh pr diff*) Bash(gh api*) Bash(git log*) Bash(git show*) Read Grep Glob
---

You are running a multi-agent code review on GitHub PR #$ARGUMENTS.

## Step 1 — Fetch the PR

Run:
- `gh pr view $ARGUMENTS --json number,title,baseRefName,headRefName,author,additions,deletions,changedFiles,body`
- `gh pr diff $ARGUMENTS`

If the PR does not exist or `$ARGUMENTS` is empty, stop and report.

## Step 2 — Determine which reviewers apply

Inspect changed paths in the PR diff:
- Always run: `kotlin-architect`, `pci-security-reviewer`, `test-quality-reviewer`.
- `android-reviewer`: paths under `android/**`, `composeApp/**`, or Android SDK imports.
- `ios-swift-reviewer`: paths under `ios/**`, `iosApp/**`, `*.swift`, or `Info.plist`.
- `emv-nfc-expert`: paths under `shared/**` in `tlv/`, `emv/`, `brand/`, `extract/`, `validation/`, or any file referencing APDU / TLV / AID / PAN / Track2.

State the chosen set before dispatching.

## Step 3 — Dispatch in parallel

Use the Agent tool. Single message with multiple parallel tool calls.

Each agent prompt includes:
1. PR metadata (number, title, author, base, head, file count, line counts).
2. Full PR unified diff.
3. Repo root path; CLAUDE.md is at the root.
4. Reminder to follow the AGENT.md output format and cite `file:line`.

## Step 4 — Aggregate

Render a consolidated report (same format as `/review`), and additionally suggest a one-paragraph PR review summary that the user can paste into GitHub. Do NOT post the comment automatically — just produce the text.

## Step 5 — No editing

Read-only. Do not modify, stage, commit, push, or post comments.
