---
name: review-emv
description: Focused security + spec review. Dispatches only emv-nfc-expert and pci-security-reviewer on the local diff. Use when a change is narrowly scoped to TLV / EMV / PAN / track2 handling and you want fast spec + PCI feedback without the full multi-agent run.
allowed-tools: Bash(git diff*) Bash(git status*) Bash(git log*) Bash(git show*) Read Grep Glob
---

You are running a narrow review focused on EMV spec correctness and PCI safety.

## Step 1 — Capture the diff

Run in parallel:
- `git status --short`
- `git diff HEAD`
- `git diff --staged`

If empty, output `No changes to review.` and stop.

## Step 2 — Dispatch

Use the Agent tool to invoke, in a single parallel batch:
- `emv-nfc-expert`
- `pci-security-reviewer`

Prompt for each:
1. Full unified diff.
2. Pointer to `CLAUDE.md` and `docs/threat-model.md` at the repo root.
3. Reminder to follow the AGENT.md output format.

## Step 3 — Aggregate

Render both outputs verbatim with this header:

```
# EMV + PCI focused review

**Diff scope:** <N files, +X / -Y lines>
**Reviewers run:** emv-nfc-expert, pci-security-reviewer
```

Then both agent reports, then an aggregate verdict line:

```
## Aggregate verdict
**Overall:** REQUEST_CHANGES | NEEDS_DISCUSSION | APPROVE
```

## Step 4 — No editing

Read-only.
