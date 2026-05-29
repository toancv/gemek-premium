# Apartment Management — Agentic SDLC

## Quick Start (3 steps)

### 1. Setup
```bash
# Copy this folder into your project
cp -r apartment-agentic-sdlc/ ~/your-project/
cd ~/your-project

# Install Claude Code if not already done
npm install -g @anthropic-ai/claude-code
claude login

git init && git add . && git commit -m "chore: agentic sdlc setup"
```

### 2. Kickoff
```bash
claude
```
Paste the contents of `KICKOFF-PROMPT.md` and press Enter.
Walk away.

### 3. Your workflow

| What you see | What you do |
|-------------|------------|
| `⏸ GATE G1 — Report ready` | Open `reports/G1-techstack.html` in browser, reply `approve G1` |
| `⏸ GATE G2 — Report ready` | Open `reports/G2-backend.html`, reply `approve G2` |
| `⏸ GATE G3 — Report ready` | Open `reports/G3-frontend.html`, reply `approve G3` |
| `⏸ GATE G4 — Report ready` | Open `reports/G4-testing.html`, reply `approve G4` |
| `⏸ BLOCKER — Decision required` | Open `reports/BLOCKER-*.html`, reply `choose A` or `choose B` |

---

## If Session Resets (context lost)

```bash
claude
```
Paste this:
```
Read PROGRESS.md and DECISIONS.md, then resume from where you left off.
```

---

## File Structure
```
/
├── CLAUDE.md              ← Agent instructions (don't edit during run)
├── KICKOFF-PROMPT.md      ← Your starting prompt
├── PROGRESS.md            ← Auto-updated by agents
├── DECISIONS.md           ← Auto-updated by agents
├── reports/               ← HTML reports (open in browser)
├── reports-template/      ← Templates used by agents
├── docs/                  ← Architecture, schema, API spec (auto-generated)
├── .claude/agents/        ← Agent role definitions
└── .claude/rules/         ← Always-on constraints
```

---

## Your Approval Commands

| Command | Effect |
|---------|--------|
| `approve G1` | Approve techstack, start coding |
| `approve G1, note: use PostgreSQL` | Approve with adjustment |
| `approve G2` | Backend approved, start frontend |
| `approve G3` | Frontend approved, start testing |
| `approve G4` | All tests pass, proceed to deploy prep |
| `choose A` | Resolve a BLOCKER with option A |
| `choose B, note: but limit to 3 retries` | Resolve with modification |
