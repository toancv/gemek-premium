# NEW-SESSION — Read this at the start of every Claude Code session

Purpose: quickly reload context at the start of a fresh session (blank context), to avoid repeating past mistakes and avoid redoing completed work.

---

## Step 1 — Read context in order (MANDATORY, before doing anything)

1. `PROGRESS.md` — current state: phase, which gates are approved, which modules are done, what is blocked.
2. `DECISIONS.md` — all architectural decisions and their rationale. Read this to understand "why the code is the way it is" and to know what is **stub / deferred** (do not treat a stub as a finished feature).
3. `SECURITY_AUDIT_PROGRESS.md` — status of the 22 security findings (fixed / deferred). SEC-20 is deferred by an architectural decision — do NOT fix it on your own.
4. `CLAUDE.md` — agent role, approval gates, workflow, quality bars.
5. When technical detail is needed: `docs/API-SPEC.md`, `docs/ARCHITECTURE.md`, `docs/DB-SCHEMA.sql`.

If there is a progress file for work in progress (e.g. `reports/test-gap-progress.md`), read it to know where to resume.

**These files are the SOURCE OF TRUTH.** If memory or any summary conflicts with the files, trust the files.

---

## Step 2 — Operating rules (apply throughout the session)

**Output & context**
- Write results directly to files. Do NOT print code/diffs/long content to chat (avoids the API content filter blocking output, and avoids bloating context).
- One task per turn. Keep replies short — one line per task.
- Update the relevant progress file after each completed task, so progress survives a dropped session.

**Crash-safety for heavy work**
- Write to a temporary `.tmp` file, then rename on completion (never leave a half-written file).
- Save raw output (e.g. coverage) to a separate file; if complete raw output already exists, reuse it instead of rebuilding from scratch.

**ECC (the Claude Code plugin)**
- Activate skills/agents by their exact NAME: `tdd-workflow`, `tdd-guide`, `springboot-tdd`, `springboot-verification`, `verification-loop`, `refactor-cleaner`, `/test-coverage`, `/code-review`. ECC matches on the skill name, not on prose descriptions.
- If the **GateGuard** hook blocks the first Bash command of the session (demanding "present facts"): present the facts once, or start the session with `ECC_GATEGUARD=off`, or add `pre:bash:gateguard-fact-force` to `ECC_DISABLED_HOOKS`.
- Context filling up fast is usually caused by too many MCP servers — consider disabling some via `/mcp` (keep < 10 MCPs, < 80 tools active).

**Git & tests**
- Tests must be GREEN before committing. Never commit broken code.
- Commit in clearly separated groups: `fix` / `test` / `docs` / `chore` kept distinct. Do NOT mix production-code changes into a test commit. Stage only the files belonging to that group — never blind `git add -A`.
- Before reporting "committed", verify with `git log --oneline` and `git status`; do not reply optimistically when the push is not confirmed.

**Gates & decisions**
- Respect the approval gates in CLAUDE.md: STOP and wait for the CTO (human) to approve. Do NOT approve your own gate.
- On a significant decision (architecture, data model, auth): create a BLOCKER report and wait for the CTO — do not pick one option and continue.

**Documentation / features**
- When writing docs or describing features: derive information from the actual code/docs. Mark `[TODO: verify]` when unsure. Do NOT invent features.

---

## Step 3 — Confirm before starting work

After finishing Step 1, reply SHORT (a few lines):
- Current phase / gate.
- The most recently completed task.
- The next task (if a progress file makes it explicit) or "awaiting instruction".

Then wait for me to assign a specific task. Do not start a large task on your own before it is assigned.