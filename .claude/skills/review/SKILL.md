---
name: review
description: Run a multi-agent code review on the current uncommitted + staged diff. Dispatches kotlin-architect, android-reviewer, ios-swift-reviewer, emv-nfc-expert, pci-security-reviewer, and test-quality-reviewer in parallel. Each agent only contributes if its scope is touched.
allowed-tools: Bash(git diff*) Bash(git status*) Bash(git log*) Bash(git show*) Read Grep Glob
---

You are running a multi-agent code review on the local diff. Follow this workflow exactly.

## Step 1 — Capture the diff

Run, in parallel:
- `git status --short`
- `git diff HEAD` (working tree changes vs HEAD)
- `git diff --staged` (index changes)

If both diffs are empty, output `No changes to review.` and stop.

## Step 2 — Determine which reviewers apply

Inspect changed paths:
- Always run: `kotlin-architect`, `pci-security-reviewer`, `test-quality-reviewer`.
- Run `android-reviewer` only if any path matches `android/**`, `composeApp/**`, or files using `androidx.*` / `android.*` imports.
- Run `ios-swift-reviewer` only if any path matches `ios/**`, `iosApp/**`, or `*.swift`, or `Info.plist`.
- Run `emv-nfc-expert` only if any path matches `shared/**` under `tlv/`, `emv/`, `brand/`, `extract/`, `validation/`, or any file referencing APDU / TLV / AID / PAN / Track2.

State the chosen set explicitly before dispatching.

## Step 3 — Dispatch in parallel

Use the Agent tool. Send all selected agents in a SINGLE message with multiple parallel tool calls — never sequentially.

For each agent, the prompt must include:
1. The full unified diff (working + staged combined).
2. A pointer: "The project's CLAUDE.md is at the repo root — read it before reviewing."
3. The repo root path so they can read referenced files.
4. A reminder: "Cite file:line. Output the exact format defined in your AGENT.md. Verdict only APPROVE / REQUEST_CHANGES / NEEDS_DISCUSSION."

Do NOT pass them additional instructions beyond their AGENT.md scope. They are the experts; they know what to look for.

## Step 4 — Aggregate

Once all agents return, render a single consolidated report:

```
# Multi-agent review

**Diff scope:** <N files, +X / -Y lines>
**Reviewers run:** <list>

---

<paste each agent's full output verbatim, in this order:
 kotlin-architect, emv-nfc-expert, pci-security-reviewer,
 android-reviewer, ios-swift-reviewer, test-quality-reviewer>

---

## Aggregate verdict

| Reviewer | Verdict | Blockers | Concerns |
|----------|---------|----------|----------|
| ...

**Overall:** REQUEST_CHANGES if any reviewer requested changes; NEEDS_DISCUSSION if any flagged ambiguity but none blocked; APPROVE only if all approved.
```

## Step 5 — No editing

This skill is read-only. Do not modify any file. Do not stage. Do not commit. Output the report and stop.
